package uk.ac.imperial.lsds.seep.multi;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MultiOperator {

	private static int		threads	= Utils.THREADS;

	private Set<SubQuery>	subQueries;
	private int				id;

	private ITaskDispatcher	dispatcher;
	
	private TaskQueue queue;
	private TaskProcessorPool workerPool;
	private Executor executor;
	
	int nqueries = 1;
	int nclasses = 2;
	
	int [][] policy;

	public MultiOperator (Set<SubQuery> subQueries, int id) {
		this.subQueries = subQueries;
		this.id = id;
		
		this.nqueries = this.subQueries.size();
	}

	public void processData (byte[] values) {

		this.dispatcher.dispatch (values, values.length);
	}
	
	public void processData (byte[] values, int length) {

		this.dispatcher.dispatch (values, length);
	}

	public void processDataSecond (byte [] values) {

		this.dispatcher.dispatchSecond(values,  values.length);
	}
	
	public void processDataSecond (byte [] values, int length) {

		this.dispatcher.dispatchSecond(values,  length);
	}

	public void setup() {
		
		this.policy = new int [nclasses][nqueries];
		for (int i = 0; i < nclasses; i++) {
			for (int j = 0; j < nqueries; j++) {
				policy [i][j] = 1;
			}
		}
		
		this.queue = new TaskQueue(threads, nqueries);
		this.workerPool = new TaskProcessorPool(threads, queue, policy, Utils.HYBRID);
		this.executor = Executors.newCachedThreadPool();
		this.queue = workerPool.start(executor);

		for (SubQuery q : this.subQueries) {
			q.setParent(this);
			q.setup();
			if (q.isMostUpstream())
				this.dispatcher = q.getTaskDispatcher();
		}
		
		Thread monitor = new Thread(new PerformanceMonitor(this));
		monitor.setName("Monitor");
		monitor.start();
	}

	public int getId() {
		return id;
	}
	
	public TaskQueue getExecutorQueue() {
		return this.queue;
	}
	
	public int getExecutorQueueSize() {
		return this.queue.size();
	}

	public Set<SubQuery> getSubQueries() {
		return this.subQueries;
	}
	
	public TaskProcessorPool getTaskProcessorPool () { 
		return workerPool; 
	}

	public void updatePolicy(int[][] policy_) {
		for (int i = 0; i < nclasses; i++)
			for (int j = 0; j < nqueries; j++)
				policy[i][j] = policy_[i][j];
	}

	public Object policyToString() {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < nclasses; i++) {
			for (int j = 0; j < nqueries; j++) {
				if (i == nclasses && j == nqueries)
					b.append(String.format("[%d][%d]=%4d",  i, j, policy[i][j]));
				else
					b.append(String.format("[%d][%d]=%4d ", i, j, policy[i][j]));
			}
		}
		b.append("]");
		return b.toString();
	}
}
