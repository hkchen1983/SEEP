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
package uk.ac.imperial.lsds.seep.acita15.operators;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import uk.ac.imperial.lsds.seep.GLOBALS;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.comm.serialization.messages.TuplePayload;
import uk.ac.imperial.lsds.seep.operator.StatelessOperator;

import uk.ac.imperial.lsds.seep.acita15.heatmap.*;

public class LocationSource implements StatelessOperator {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(Source.class);
	private final BlockingQueue<String> heatMapQueue = new LinkedBlockingQueue<>();
	
	public void setUp() {
		System.out.println("Setting up LOCATION_SOURCE operator with id="+api.getOperatorId());
	}

	public void processData(DataTuple dt) {
		Map<String, Integer> mapper = api.getDataMapper();
		DataTuple data = new DataTuple(mapper, new TuplePayload());
		
		long tupleId = 0;
		
		boolean sendIndefinitely = Boolean.parseBoolean(GLOBALS.valueFor("sendIndefinitely"));
		long numTuples = Long.parseLong(GLOBALS.valueFor("numTuples"));
		int tupleSizeChars = Integer.parseInt(GLOBALS.valueFor("tupleSizeChars"));
		boolean rateLimitSrc = Boolean.parseBoolean(GLOBALS.valueFor("rateLimitSrc"));
		long frameRate = Long.parseLong(GLOBALS.valueFor("frameRate"));
		long interFrameDelay = 1000 / frameRate;
		logger.info("Source inter-frame delay="+interFrameDelay);
		
		int x = Integer.parseInt(GLOBALS.valueFor("x"));
		int y = Integer.parseInt(GLOBALS.valueFor("y"));
		
		//int xTiles = Integer.parseInt(GLOBALS.valueFor("xTiles"));
		//int yTiles = Integer.parseInt(GLOBALS.valueFor("yTiles"));
		int xTiles = 10;
		int yTiles = 10;

		long interLocDelay = interFrameDelay;
		long heatMapInterval = 10 * interFrameDelay;
		final HeatMapThread localHeatMapper = new HeatMapThread(x, y, xTiles, yTiles, interLocDelay, heatMapInterval);
		new Thread(localHeatMapper).start();
		
		//final String value = generateFrame(tupleSizeChars);
		final long tStart = System.currentTimeMillis();
		logger.info("Source sending started at t="+tStart);
		logger.info("Source sending started at t="+tStart);
		logger.info("Source sending started at t="+tStart);
		
		String value = null;
		while(sendIndefinitely || tupleId < numTuples){
			
			try
			{
				value = heatMapQueue.take();
			} catch (InterruptedException e) { throw new RuntimeException(e); }
			DataTuple output = data.newTuple(tupleId, value);
			output.getPayload().timestamp = tupleId;
			if (tupleId % 1000 == 0)
			{
				logger.info("Source sending tuple id="+tupleId+",t="+output.getPayload().instrumentation_ts);
			}
			else
			{
				logger.debug("Source sending tuple id="+tupleId+",t="+output.getPayload().instrumentation_ts);
			}
			api.send_highestWeight(output);
			
			tupleId++;
			
			long tNext = tStart + (tupleId * interFrameDelay);
			long tNow = System.currentTimeMillis();
			if (tNext > tNow && rateLimitSrc)
			{
				logger.debug("Source wait to send next frame="+(tNext-tNow));
				try {
					Thread.sleep(tNext - tNow);
				} 
				catch (InterruptedException e) {
					e.printStackTrace();
				}				
			}
		}
		System.exit(0);
	}
	
	public void processData(List<DataTuple> arg0) {
		// TODO Auto-generated method stub
		
	}
	
	private class HeatMapThread implements Runnable
	{
		private final HeatMap current;
		private final long locUpdateInterval;
		private final long heatMapInterval;
		
		public HeatMapThread(double tileWidth, double tileHeight, int xTiles, int yTiles, long locUpdateInterval, long heatMapInterval)
		{
			this.current = new HeatMap(tileWidth, tileHeight, xTiles, yTiles);
			this.locUpdateInterval = locUpdateInterval;
			this.heatMapInterval = heatMapInterval;
		}
		
		@Override
		public void run()
		{

			long tStart = System.currentTimeMillis();
			while(true)
			{
				Location loc = getCurrentLocation();
				current.updatePos(loc);
				try {
					Thread.sleep(locUpdateInterval);
				} 
				catch (InterruptedException e) {
					e.printStackTrace();
				}	
				long tNow = System.currentTimeMillis();
				if (tNow - tStart > heatMapInterval)
				{
					heatMapQueue.add(current.toString());
					current.reset();
					tStart = tNow;
				}
			}
		}
		
		private Location getCurrentLocation()
		{
			File file = new File("node.xyz");
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			    String line;
			    while ((line = br.readLine()) != null) {
			       logger.debug("Current node loc="+line);
			       return new Location(line.trim());
			    }
			}
			catch(IOException e) { throw new RuntimeException(e); }
			
			throw new RuntimeException("No location found!");
		}
	}
}
