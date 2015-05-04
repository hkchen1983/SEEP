package net;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import uk.ac.imperial.lsds.seep.multi.IMicroOperatorCode;
import uk.ac.imperial.lsds.seep.multi.IQueryBuffer;
import uk.ac.imperial.lsds.seep.multi.ITupleSchema;
import uk.ac.imperial.lsds.seep.multi.IWindowAPI;
import uk.ac.imperial.lsds.seep.multi.TheGPU;
import uk.ac.imperial.lsds.seep.multi.UnboundedQueryBufferFactory;
import uk.ac.imperial.lsds.seep.multi.Utils;
import uk.ac.imperial.lsds.seep.multi.WindowBatch;
import uk.ac.imperial.lsds.streamsql.op.gpu.KernelCodeGenerator;

public class LRBUDFGPU implements IMicroOperatorCode {
	/*
	 * This is the UDF for the Linear Road Benchmark.
	 * 
	 * Given a window batch, this UDF first partitions each window 
	 * by vehicle id (attribute #x in the input stream). 
	 * 
	 * Instead of aggregating values though, it maintains the last 
	 * observed entry for a particular vehicle.
	 * 
	 * The result is a sparse hash table H(i) for each window W(i).
	 * 
	 * In the second phase, we produce the stream: 
	 * 
	 * H(1) - H(0), H(2) - H(1), H(3) - H(2), and so on.
	 * 
	 * Note that there will always be a W(0) representing the last 
	 * window of the previously processed window batch.
	 */
	
	private static final int threadsPerGroup = 256;
	private static final int tuplesPerThread = 2;
	
	private static int kdbg = 0;
	private static boolean debug = false;
	
	private static final int pipelines = 2;
	private int [] taskIdx;
	private int [] freeIdx;
	
	private static final int _hash_functions = 5;
	
	private static final float _scale_factor = 1.25F;
	
	private static final float _min_space_requirements [] = {
		Float.MAX_VALUE,
		Float.MAX_VALUE,
		2.01F,
		1.10F,
		1.03F,
		1.02F
	};
	
	/* Default stash table size (# tuples) */
	private static int _stash = 100;
	
	private ITupleSchema inputSchema;
	
	private static String filename = Utils.SEEP_HOME + "/seep-system/clib/templates/udfs/AggregationIStream.cl";
	
	private int qid;
	
	private int [] args;
	
	private int tuples;
	
	private int [] threads;
	private int [] tgs;
	
	private int ngroups;
	
	private int batchSize = -1, windowSize = -1;

	/* Global and local memory sizes */
	private int inputSize = -1, outputSize;
	private int windowPtrsSize;
	private int localInputSize;
	
	private int __stash_x, __stash_y;
	private int [] _x;
	private int [] _y;
	
	private ByteBuffer x;
	private ByteBuffer y;
	
	private int iterations;
	
	private int tableSize, tableSlots;
	
	private byte [] startPtrs;
	private byte [] endPtrs;
	
	private int intermediateTupleSize, outputTupleSize;
	
	private byte [] contents, stashed, failed, attempts, indices, offsets, partitions;
	private int contentsLength,
	             stashedLength, 
	              failedLength, 
	            attemptsLength, 
	             indicesLength, 
	             offsetsLength, 
	          partitionsLength;
	
	private static boolean isPowerOfTwo (int n) {
		if (n == 0)
			return false;
		while (n != 1) {
			if (n % 2 != 0)
				return false;
			n = n/2;
		}
		return true;
	}
	
	private static int computeIterations (int n) {
		int result = 7;
		float logn = (float) (Math.log(n) / Math.log(2.0));
		return (int) (result * logn);
	}
	
	private static void constants (int [] x, int [] y, int [] stash) {
		Random r = new Random();
		int prime = 2147483647;
		assert (x.length == y.length);
		int i, n = x.length;
		int t;
		for (i = 0; i < n; i++) {
			t = r.nextInt(prime);
			x[i] = (1 > t ? 1 : t);
			y[i] = r.nextInt(prime) % prime;
		}
		/* Stash hash constants */
		stash[0] = Math.max(1, r.nextInt(prime)) % prime;
		stash[1] = r.nextInt(prime) % prime;
	}
	
	private void printWindowPointers (byte [] startPtrs, byte [] endPtrs) {
		
		ByteBuffer b = ByteBuffer.wrap(startPtrs).order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer d = ByteBuffer.wrap(  endPtrs).order(ByteOrder.LITTLE_ENDIAN);
		int wid = 0;
		while (b.hasRemaining() && d.hasRemaining()) {
			System.out.println(String.format("w %02d: starts %10d ends %10d", 
				wid, b.getInt(), d.getInt()));
				wid ++;
		}
	}
	
	public LRBUDFGPU () {
	}
	
	public LRBUDFGPU (ITupleSchema inputSchema) {
		
		this.inputSchema = inputSchema;
		
		/* The output schema is the same as the input schema */
		this.outputTupleSize = inputSchema.getByteSizeOfTuple();
		System.out.println(String.format("[DBG] output tuple size is %d bytes", this.outputTupleSize));
		/*
		 * In the intermediate data representation of a tuple, there is no need to
		 * hold a value, nor a count per tuple. Just a tuple.
		 */
		this.intermediateTupleSize = outputTupleSize;
		System.out.println(String.format("[DBG] intermediate tuple size is %d bytes", this.intermediateTupleSize));
		
		taskIdx = new int [pipelines];
		freeIdx = new int [pipelines];
		for (int i = 0; i < pipelines; i++) {
			taskIdx[i] = -1;
			freeIdx[i] = -1;
		}
	}
	
	public void setInputSize (int inputSize) {
		this.inputSize = inputSize;
	}
	
	public void setBatchSize (int batchSize) {
		this.batchSize = batchSize;
	}
	
	public void setWindowSize (int windowSize) {
		this.windowSize = windowSize;
	}
	
	public void setup () {
		
		if (windowSize < 0) {
			System.err.println("error: invalid window size");
			System.exit(1);
		}
		if (batchSize < 0) {
			System.err.println("error: invalid batch size");
			System.exit(1);
		}
		if (inputSize < 0) {
			System.err.println("error: invalid input size");
			System.exit(1);
		}
		tuples = inputSize / inputSchema.getByteSizeOfTuple();
		
		windowPtrsSize = batchSize * 4;
		startPtrs = new byte [windowPtrsSize];
		  endPtrs = new byte [windowPtrsSize];
		
		/* Configure hash table constants */
		_x = new int [_hash_functions];
		_y = new int [_hash_functions];
		 x = ByteBuffer.allocate(4 * _hash_functions).order
			(ByteOrder.LITTLE_ENDIAN);
		 y = ByteBuffer.allocate(4 * _hash_functions).order
			(ByteOrder.LITTLE_ENDIAN);
		int [] stash = new int[2];
		constants (_x, _y, stash);
		__stash_x = stash[0];
		__stash_y = stash[1];
		for (int i = 0; i < _hash_functions; i++) {
			x.putInt(_x[i]);
			y.putInt(_y[i]);
		}
		iterations = computeIterations (windowSize);
		/* Determine an upper bound on # slots/table,
		 * such that we avoid collisions */
		System.out.println(String.format("[DBG] %d iterations\n", iterations));
		float alpha = _scale_factor;
		if (alpha < _min_space_requirements[_hash_functions])
		{
			throw new IllegalArgumentException("error: invalid scale factor");
		}
		tableSize  = (int) Math.ceil(windowSize * alpha);
		tableSlots = tableSize + _stash;
		System.out.println(String.format("[DBG] # slots (~2) is %4d", tableSlots));
		while (! isPowerOfTwo(tableSlots)) {
			tableSlots += 1;
		}
		System.out.println(String.format("[DBG] # slots (^2) is %4d", tableSlots));
		tableSize = tableSlots - _stash;
		
		/* Determine #threads */
		tgs = new int [4];
		tgs[0] = threadsPerGroup; /* This is a constant; it must be a power of 2 */
		tgs[1] = threadsPerGroup;
		tgs[2] = threadsPerGroup;
		tgs[3] = threadsPerGroup;
		
		threads = new int [4];
		threads[0] = (batchSize * tableSlots); /* Clear `indices` and `offsets` */
		threads[1] = batchSize * tgs[0];
		/* Configure scan & compact kernels */
		threads[2] = (batchSize * tableSlots) / tuplesPerThread;
		threads[3] = (batchSize * tableSlots) / tuplesPerThread;
		ngroups    = (batchSize * tableSlots) / tuplesPerThread / tgs[0];
		
		/* The output is simply a function of the number of windows 
 		 * 
 		 * Assume output tuple schema is <long, int key, float value> (16 bytes) 
 		 */
		outputSize = 2 * tuples * outputTupleSize;
		System.out.println("[DBG] output size is " + outputSize + " bytes");
		
		/* Intermediate state */
		
		  contentsLength = intermediateTupleSize * batchSize * tableSlots;
		   stashedLength = 4 * batchSize;
		    failedLength = 4 * batchSize;
		  attemptsLength = 4 * batchSize;
		   indicesLength = 4 * batchSize * tableSlots;
		   offsetsLength = 4 * batchSize * tableSlots;
		partitionsLength = 4 *   ngroups;
		
		if (kdbg > 0) {
		contents   = new byte [  contentsLength];
		stashed    = new byte [   stashedLength];
		failed     = new byte [    failedLength];
		attempts   = new byte [  attemptsLength];
		indices    = new byte [   indicesLength];
		offsets    = new byte [   offsetsLength];
		partitions = new byte [partitionsLength];
		}
		
		String source = 
			KernelCodeGenerator.load(filename);
		System.out.println(source);
		
		qid = TheGPU.getInstance().getQuery(source, 5, 5, 8);
		
		TheGPU.getInstance().setInput(qid, 0, inputSize);
		/* Start and end pointers */
		TheGPU.getInstance().setInput(qid, 1, startPtrs.length);
		TheGPU.getInstance().setInput(qid, 2,   endPtrs.length);
		/* Hash function constants, x & y */
		TheGPU.getInstance().setInput(qid, 3, x.array().length);
		TheGPU.getInstance().setInput(qid, 4, y.array().length);
		
		int move = (kdbg > 0) ? 0 : 1;
		
		TheGPU.getInstance().setOutput(qid, 0,   contentsLength, 0, move, 0, 0);
		TheGPU.getInstance().setOutput(qid, 1,    stashedLength, 0, move, 0, 0);
		TheGPU.getInstance().setOutput(qid, 2,     failedLength, 0, move, 0, 0);
		TheGPU.getInstance().setOutput(qid, 3,   attemptsLength, 0, move, 0, 0);
		TheGPU.getInstance().setOutput(qid, 4,    indicesLength, 0,    0, 1, 0);
		TheGPU.getInstance().setOutput(qid, 5,    offsetsLength, 0, move, 0, 0);
		TheGPU.getInstance().setOutput(qid, 6, partitionsLength, 0, move, 0, 0);
		TheGPU.getInstance().setOutput(qid, 7,       outputSize, 1,    0, 0, 1);
		
		localInputSize = 4 * tgs[0] * tuplesPerThread; 
		
		args = new int [8];
		args[0] = tuples;
		args[1] = 0; /* dummyParam1; */
		args[2] = 0; /* dummyParam2; */
		args[3] = tableSize;
		args[4] = __stash_x;
		args[5] = __stash_y;
		args[6] = iterations;
		args[7] = localInputSize;
		
		TheGPU.getInstance().setKernelAggregateIStream(qid, args);
	}
	
	@Override
	public String toString () {
		final StringBuilder sb = new StringBuilder();
		sb.append("LRB UDF");
		return sb.toString();
	}
	
	@Override
	public void processData (WindowBatch windowBatch, IWindowAPI api) {
		
		int currentTaskIdx = windowBatch.getTaskId();
		int currentFreeIdx = windowBatch.getFreeOffset();
		
		/* Set input */
		byte [] inputArray = windowBatch.getBuffer().array();
		int start = windowBatch.normalise(windowBatch.getBatchStartPointer());
		int end   = windowBatch.normalise(windowBatch.getBatchEndPointer());
		
		if (end > windowBatch.getBuffer().capacity()) {
			System.err.println(String.format("warning: batch end pointer (%d) is greater than its buffer size (%d)", 
				end, windowBatch.getBuffer().capacity()));
			System.exit(1);
		}
		
		TheGPU.getInstance().setInputBuffer(qid, 0, inputArray, start, end);
		
		windowBatch.initWindowPointers(startPtrs, endPtrs);
		if (debug)
			printWindowPointers (startPtrs, endPtrs);
		
		TheGPU.getInstance().setInputBuffer(qid, 1, startPtrs);
		TheGPU.getInstance().setInputBuffer(qid, 2,   endPtrs);
		
		/* Hash table constants */
		TheGPU.getInstance().setInputBuffer(qid, 3, x.array());
		TheGPU.getInstance().setInputBuffer(qid, 4, y.array());
		
		/* Set output */
		if (kdbg > 0) {
		TheGPU.getInstance().setOutputBuffer(qid, 0,   contents);
		TheGPU.getInstance().setOutputBuffer(qid, 1,    stashed);
		TheGPU.getInstance().setOutputBuffer(qid, 2,     failed);
		TheGPU.getInstance().setOutputBuffer(qid, 3,   attempts);
		TheGPU.getInstance().setOutputBuffer(qid, 4,    indices);
		TheGPU.getInstance().setOutputBuffer(qid, 5,    offsets);
		TheGPU.getInstance().setOutputBuffer(qid, 6, partitions);
		}
		
		/* The output */
		IQueryBuffer outputBuffer = UnboundedQueryBufferFactory.newInstance();
		TheGPU.getInstance().setOutputBuffer(qid, 7, outputBuffer.array());
		
		TheGPU.getInstance().execute(qid, threads, tgs);
		
		/* 
		 * Set position based on the data size returned from the GPU engine
		 */
		outputBuffer.position(TheGPU.getInstance().getPosition(qid, 7));
		if (debug)
			System.out.println("[DBG] output buffer position is " + outputBuffer.position());
		
		windowBatch.setBuffer(outputBuffer);
		
		/* Print tuples
		outputBuffer.close();
		int tid = 1;
		while (outputBuffer.hasRemaining()) {
			// Each tuple is 32-bytes long and the schema is <long, int, float, int, int, int, int>
			System.out.println(String.format("%04d: %6d,%6d,%4.1f,%6d, %6d, %6d, %6d", 
			tid++,
			outputBuffer.getByteBuffer().getLong (),
			outputBuffer.getByteBuffer().getInt  (),
			outputBuffer.getByteBuffer().getFloat(),
			outputBuffer.getByteBuffer().getInt  (),
			outputBuffer.getByteBuffer().getInt  (),
			outputBuffer.getByteBuffer().getInt  (),
			outputBuffer.getByteBuffer().getInt  ()
			));
		}
		*/
		windowBatch.setTaskId     (taskIdx[0]);
		windowBatch.setFreeOffset (freeIdx[0]);
		
		for (int i = 0; i < taskIdx.length - 1; i++) {
			taskIdx[i] = taskIdx [i + 1];
			freeIdx[i] = freeIdx [i + 1];
		}
		taskIdx [taskIdx.length - 1] = currentTaskIdx;
		freeIdx [freeIdx.length - 1] = currentFreeIdx;
		
		api.outputWindowBatchResult(-1, windowBatch);
		/*
		System.err.println("Disrupted");
		System.exit(-1);
		*/
	}

	@Override
	public void processData(WindowBatch firstWindowBatch,
			WindowBatch secondWindowBatch, IWindowAPI api) {
		
		throw new UnsupportedOperationException
		("LRB UDF is a single input operator and does not operate on two streams");
	}
}
