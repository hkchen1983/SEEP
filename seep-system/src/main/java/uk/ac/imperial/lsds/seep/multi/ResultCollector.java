package uk.ac.imperial.lsds.seep.multi;

import java.util.concurrent.locks.LockSupport;

import uk.ac.imperial.lsds.seep.multi.join.JoinResultHandler;

public class ResultCollector {
	
	static long prev = -1;
	
	public static void aggregateAndFree (ResultHandler handler, SubQuery query, 
			int taskid, int freeOffset) {
		
//		if (taskid < 0) { /* Invalid task id */
	//		return ;
	//	}
	//	int idx = ((taskid - 1) % handler.SLOTS);
		
	//	try {
			
//			while (! handler.slots.compareAndSet(idx, -1, 0)) {
//				
//				System.err.println(String.format("warning: result collector blocked at %s q %d t %4d idx %4d", 
//						Thread.currentThread(), query.getId(), taskid, idx));
//				LockSupport.parkNanos(1L);
//			}
			
//			handler.offsets[idx] = freeOffset;
//			handler.results[idx] = null;
//			
//			handler.latch [idx] = 0;
			
			/* No other thread can modify this slot. */
//			handler.slots.set(idx, 1);
			
			/* Forward and free */
			
		//	if (! handler.semaphore.tryAcquire())
		//		return;
			
			/* No other thread can enter this section */
			
		//	int pid = ThreadMap.getInstance().get(Thread.currentThread().getId());
			
		//	long count = 0;
			
			// while (count++ < 1024) {
		//	while (true) {
				
		//		long first = handler.theWindowHeap.tryFirst();
		//		if (first != (prev + 1) || first < 0) {
					// System.out.println("first " + first + " previous " + prev);
		//			break;
		//		}
				
			//	Pane p = handler.theWindowHeap.remove();
		//		prev = prev + 1; // p.getPaneIndex();
	//			if (p == null)
//					break;
				
				// System.out.println("[DBG] worker " + pid + " " + p);
				
//				handler.freeBuffer.free(p.getFreeIndex());
				
//				p.release();
				/*
				if (p.getPaneIndex() > handler.theCurrentWindow.getLastPaneIndex()) {
					
//					System.out.println(String.format("[DBG] [ResultCollector] close window %3d and shift left", 
//							handler.theCurrentWindow.getWindowIndex()));
					
					handler.theCurrentWindow.closeAndShiftLeft(handler);
					
					// handler.theCurrentWindow.heap.dump();
				}
				
//				System.out.println(String.format("[DBG] [ResultCollector] shift window to the right with pane %3d", 
//						p.getPaneIndex()));
				
				handler.theCurrentWindow.shiftRight(p);
				*/
				// handler.theCurrentWindow.heap.dump();
	//		}
			
		//	handler.semaphore.release();
			
			/* End of new logic for aggregation */
			
//			/* Is slot `index` occupied? 
//			 */
//			if (! handler.slots.compareAndSet(handler.next, 1, 2)) {
//				handler.semaphore.release();
//				return ;
//			}
			
			
			
			
//			boolean busy = true;
//			
//			while (busy) {
//				
//				IQueryBuffer buf = handler.results[handler.next];
//				
//				if (buf != null) {
//				
//				byte [] arr = buf.array();
//				if (query.getNumberOfDownstreamSubQueries() > 0) {
//					int pos = handler.latch[handler.next];
//					for (int i = pos; i < query.getNumberOfDownstreamSubQueries(); i++) {
//						if (query.getDownstreamSubQuery(i) != null) {
//							boolean result = false;
//							if (query.isLeft()) {
//								result = query.getDownstreamSubQuery(i).getTaskDispatcher().tryDispatchFirst( arr, buf.position()); // arr.length);
//							} else {
//								result = query.getDownstreamSubQuery(i).getTaskDispatcher().tryDispatchSecond(arr, buf.position()); // arr.length);
//							}
//							if (! result) {
//								handler.latch[handler.next] = i;
//								handler.slots.set(handler.next, 1);
//								
//								return;
//							}
//						}
//					}
//				}
//				
//				}
//				
//				/* Forward to the distributed API */
//				
//				if (buf != null) {
//					handler.incTotalOutputBytes(buf.position());
//					buf.release();
//				}
//				
//				/* Free input buffer */
//				int offset = handler.offsets[handler.next];
//				if (offset != Integer.MIN_VALUE) {
//					
//					handler.freeBuffer.free (offset);
//				} else {
//					System.err.println(String.format("[DBG] %s skip slot qid %d idx %6d", 
//							Thread.currentThread(), query.getId(), handler.next));
//					System.exit(1);
//				}
//				
//				/* Release the current slot */
//				handler.slots.set(handler.next, -1);
//				
//				/* Increment next */
//				handler.next = handler.next + 1;
//				handler.next = handler.next % handler.SLOTS;
//				
//				/* Check if next is ready to be pushed */
//				
//				if (! handler.slots.compareAndSet(handler.next, 1, 2)) {
//					busy = false;
//				 }
//			}
//			/* Thread exit critical section */
//			handler.semaphore.release();
			
	//	} catch (Exception e) {
	//		e.printStackTrace();
	//	}
	}

	public static void forwardAndFree (ResultHandler handler, SubQuery query, IQueryBuffer buffer, 
			int taskid, int freeOffset, int latencyMark, boolean GPU) {
		
		if (taskid < 0) { /* Invalid task id */
			return ;
		}
		int idx = ((taskid - 1) % handler.SLOTS);
		
		try {
			
			while (! handler.slots.compareAndSet(idx, -1, 0)) {
				
				System.err.println(String.format("warning: result collector blocked at %s q %d t %4d idx %4d", 
						Thread.currentThread(), query.getId(), taskid, idx));
				LockSupport.parkNanos(1L);
			}
			
			handler.offsets[idx] = freeOffset;
			handler.results[idx] = buffer;
			
			handler.latch [idx] = 0;
			handler.mark  [idx] = latencyMark;
			
			/* No other thread can modify this slot. */
			handler.slots.set(idx, 1);
			
			/* Forward and free */
			
			if (! handler.semaphore.tryAcquire())
				return;
			
			/* No other thread can enter this section */
			
			/* Is slot `index` occupied? 
			 */
			if (! handler.slots.compareAndSet(handler.next, 1, 2)) {
				handler.semaphore.release();
				return ;
			}
			
			boolean busy = true;
			
			while (busy) {

				IQueryBuffer buf = handler.results[handler.next];
				byte [] arr = buf.array();
				
				/*
				 * Do the actual result forwarding
				 */
				if (query.getNumberOfDownstreamSubQueries() > 0) {
					int pos = handler.latch[handler.next];
					for (int i = pos; i < query.getNumberOfDownstreamSubQueries(); i++) {
						if (query.getDownstreamSubQuery(i) != null) {
							boolean result = false;
							if (query.isLeft()) {
								result = query.getDownstreamSubQuery(i).getTaskDispatcher().tryDispatchFirst( arr, buf.position()); // arr.length);
							} else {
								result = query.getDownstreamSubQuery(i).getTaskDispatcher().tryDispatchSecond(arr, buf.position()); // arr.length);
							}
							if (! result) {
								handler.latch[handler.next] = i;
								handler.slots.set(handler.next, 1);
								
								return;
							}
						}
					}
				}
				
				/* Forward to the distributed API */

				/* Measure latency */
				if (handler.mark[handler.next] != -1)
					query.getLatencyMonitor().monitor(handler.freeBuffer, handler.mark[handler.next]);
				
				/* Before releasing the buffer, count how many bytes are in the output.
				 * 
				 * It is important that all operators set the position of the buffer accordingly.
				 * 
				 * The assumption is that `buf` is an intermediate buffer and that the start
				 * position is 0.
				 */
				handler.incTotalOutputBytes(buf.position());
				buf.release();

				/* Free input buffer */
				int offset = handler.offsets[handler.next];
				if (offset != Integer.MIN_VALUE) {
					
					handler.freeBuffer.free (offset);
				} else {
					System.err.println(String.format("[DBG] %s skip slot qid %d idx %6d", 
							Thread.currentThread(), query.getId(), handler.next));
					System.exit(1);
				}
				
				/* Release the current slot */
				handler.slots.set(handler.next, -1);
				
				/* Increment next */
				handler.next = handler.next + 1;
				handler.next = handler.next % handler.SLOTS;
				
				/* Check if next is ready to be pushed */
				
				if (! handler.slots.compareAndSet(handler.next, 1, 2)) {
					busy = false;
				 }
				
			}
			/* Thread exit critical section */
			handler.semaphore.release();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void forwardAndFree (
		JoinResultHandler handler, 
		SubQuery query,
		IQueryBuffer buffer, 
		int taskid, 
		int freeOffset1, 
		int freeOffset2,
		int latencyMark
	) {
		
		// System.out.println(String.format("[DBG] task %d free offsets 1/%d 2/%d", taskid, freeOffset1, freeOffset2));
		
		if (taskid < 0) { /* Invalid task id */
			return ;
		}
		
		int idx = ((taskid - 1) % handler.SLOTS);
		
		try {
			
			while (! handler.slots.compareAndSet(idx, -1, 0)) {
				
				System.err.println(String.format("warning: result collector blocked at %s q %d t %4d idx %4d", 
						Thread.currentThread(), query.getId(), taskid, idx));
				LockSupport.parkNanos(1L);
			}
			
			handler.firstOffsets[idx] =  freeOffset1;
			handler.secondOffsets[idx] = freeOffset2;
			
			handler.results[idx] = buffer;
			
			handler.latch [idx] = 0; 
			handler.mark  [idx] = latencyMark;
			
			/* No other thread can modify this slot. */
			handler.slots.set(idx, 1);
			
			/* Forward and free */
			
			if (! handler.semaphore.tryAcquire())
				return;
			
			/* No other thread can enter this section */
			
			/* Is slot `index` occupied? 
			 */
			if (! handler.slots.compareAndSet(handler.next, 1, 2)) {
				handler.semaphore.release();
				return ;
			}
			
			boolean busy = true;
			
			while (busy) {

				IQueryBuffer buf = handler.results[handler.next];
				// buf.close();
				byte [] arr = buf.array();
				
				/*
				 * Do the actual result forwarding
				 */
				if (query.getNumberOfDownstreamSubQueries() > 0) {
					int pos = handler.latch[handler.next];
					for (int i = pos; i < query.getNumberOfDownstreamSubQueries(); i++) {
						if (query.getDownstreamSubQuery(i) != null) {
							boolean result = false;
							if (query.isLeft()) {
								result = query.getDownstreamSubQuery(i).getTaskDispatcher().tryDispatchFirst( arr, buf.position()); // arr.length);
							} else {
								result = query.getDownstreamSubQuery(i).getTaskDispatcher().tryDispatchSecond(arr, buf.position()); // arr.length);
							}
							if (! result) {
								handler.latch[handler.next] = i;
								handler.slots.set(handler.next, 1);
								
								return;
							}
						}
					}
				}
				
				/* Forward to the distributed API */

				/* Measure latency */
				if (handler.mark[handler.next] != -1)
					query.getLatencyMonitor().monitor(handler.firstFreeBuffer, handler.mark[handler.next]);
				
				/* Before releasing the buffer, count how many bytes are in the output.
				 * 
				 * It is important that all operators set the position of the buffer accordingly.
				 * 
				 * The assumption is that `buf` is an intermediate buffer and that the start
				 * position is 0.
				 */
				handler.incTotalOutputBytes(buf.position());
				buf.release();
				
				/* Free first input buffer */
				int offset1 = handler.firstOffsets[handler.next];
				if (offset1 != Integer.MIN_VALUE) {
					handler.firstFreeBuffer.free (offset1);
				}
				
				/* Free second input buffer */
				int offset2 = handler.secondOffsets[handler.next];
				if (offset2 != Integer.MIN_VALUE) {
					handler.secondFreeBuffer.free (offset2);
				}
				
				/* Release the current slot */
				handler.slots.set(handler.next, -1);
				
				/* Increment next */
				handler.next = handler.next + 1;
				handler.next = handler.next % handler.SLOTS;
				
				/* Check if next is ready to be pushed */
				
				if (! handler.slots.compareAndSet(handler.next, 1, 2)) {
					busy = false;
				 }
				
			}
			/* Thread exit critical section */
			handler.semaphore.release();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}	
}

///* Build window from partial state */
//if (nextWindowEndsAt < 0) {
//	/* Find end pointer of first window */
//	nextWindowStartsAt = 0;
//	nextWindowEndsAt = query.getWindowDefinition().numberOfPanes() - 1;
//}
//
//// System.out.println("[DBG] [enter] next window ends at " + nextWindowEndsAt + " " + query.getWindowDefinition().panesPerSlide() + " panes/slide");
//
//IQueryBuffer outBuffer = UnboundedQueryBufferFactory.newInstance();
//
//while (handler.windowHeap.getLastInserted() > nextWindowEndsAt) {
//	
//	// System.out.println(String.format("[DBG] window starts at %6d ends at %6d", nextWindowStartsAt, nextWindowEndsAt));
//	
//	IntermediateMap m = handler.windowHeap.remove(currIndex);
//	// long lastRemoved = handler.windowHeap.getLastRemoved();
//	// if (handler.windowHeap.getLastRemoved() != currIndex) {
//	if (m == null) {
//		/* There is something wrong */
//		// System.out.println(String.format("warning: expected %d", currIndex));
//		break;
//	}
//	
//	panes[(int) currIndex] = m;
//	
////	if (currIndex == nextWindowStartsAt) {
////		previousPane.copy(m);
////	}
//	if (currentWindow == null) {
//		currentWindow = IntermediateMapFactory.newInstance(pid);
//	}
//	
//	// currentWindow.intersect (m);
//	// System.out.println(String.format("[DBG] current index %6d window ends at %6d", currIndex, nextWindowEndsAt));
//	
//	if (currIndex == nextWindowEndsAt) {
//		// System.out.println(String.format("[DBG] window closed at pane %d", currIndex));
//		
//		// System.out.println(String.format("[DBG] window starts at %6d ends at %6d", nextWindowStartsAt, nextWindowEndsAt));
//		 
//		for (int i = (int) nextWindowStartsAt; i < nextWindowEndsAt; i++)
//		
//			panes[i].populate(outBuffer);
//		
//		// currentWindow.clear();
//		// currentWindow.release();
//		// currentWindow = null;
//		
//		nextWindowEndsAt += query.getWindowDefinition().panesPerSlide();
//		nextWindowStartsAt += query.getWindowDefinition().panesPerSlide();
//	}
//	// m.release();
//	currIndex ++;
////	nextWindowEndsAt += query.getWindowDefinition().panesPerSlide();
////	nextWindowStartsAt += query.getWindowDefinition().panesPerSlide();
//	// System.out.println("[DBG] [loop ] next window ends at " + nextWindowEndsAt);
//}
//
//handler.results[idx] = outBuffer;
