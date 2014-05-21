/*******************************************************************************
 * Copyright (c) 2014 Imperial College London
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Raul Castro Fernandez - initial API and implementation
 ******************************************************************************/
package uk.ac.imperial.lsds.seep.operator;

import java.io.Serializable;

import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.compose.StatelessMicroOperator.OPWorker;

public class LocalApi implements Serializable, API {

	private static final long serialVersionUID = 1L;
	private OPWorker so;
	
	@Override
	public void setCallbackObject(Callback c) {
		this.so = (OPWorker)c;
	}
	
	public LocalApi(Callback c) {
		this.so = (OPWorker)c;
	}
	
	@Override
	public void send(DataTuple dt) {
		so.send(dt);
	}

	@Override
	public void send_all(DataTuple dt) {
		so.send_all(dt);
	}

	@Override
	public void send_splitKey(DataTuple dt, int key) {
		so.send_splitKey(dt, key);
	}

	@Override
	public void send_toIndex(DataTuple dt, int idx) {
		so.send_toIndex(dt, idx);
	}

	@Override
	public void send_toStreamId(DataTuple dt, int streamId) {
		so.send_toStreamId(dt, streamId);
	}

	@Override
	public void send_toStreamId_splitKey(DataTuple dt, int streamId, int key) {
		so.send_toStreamId_splitKey(dt, streamId, key);	
	}

	@Override
	public void send_toStreamId_toAll(DataTuple dt, int streamId) {
		so.send_toStreamId_toAll(dt, streamId);
	}

    @Override
    public void send_toStreamId_toAll_threadPool(DataTuple dt, int streamId) {
        so.send_toStreamId_toAll_threadPool(dt, streamId);
    }

    @Override
    public void send_all_threadPool(DataTuple dt) {
        so.send_all_threadPool(dt);
    }

    @Override
    public void send_to_OpId(DataTuple dt, int opId) {
        
    }

    @Override
    public void send_to_OpIds(DataTuple[] dt, int[] opId) {
        
    }

    @Override
    public void send_toIndices(DataTuple[] dts, int[] indices) {
        
    }
}
