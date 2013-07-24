/*******************************************************************************
 * Copyright (c) 2013 Imperial College London.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial design and implementation
 ******************************************************************************/
package uk.co.imperial.lsds.seep.comm.serialization.messages;

import java.util.ArrayList;

public class BatchTuplePayload {

	public int batchSize = 0;
	public ArrayList<TuplePayload> batch = new ArrayList<TuplePayload>();
	
	public synchronized void addTuple(TuplePayload payload){
		batch.add(payload);
		batchSize++;
	}
	
	public void clear(){
		batch = new ArrayList<TuplePayload>();
		batchSize = 0;
	}
	
	public TuplePayload getTuple(int index){
		return batch.get(index);
	}
	
	public BatchTuplePayload(){
		
	}
	
	public int size(){
		return batch.size();
	}
}
