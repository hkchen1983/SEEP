import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import uk.ac.imperial.lsds.seep.api.QueryBuilder;
import uk.ac.imperial.lsds.seep.comm.serialization.DataTuple;
import uk.ac.imperial.lsds.seep.operator.API;
import uk.ac.imperial.lsds.seep.operator.compose.micro.IMicroOperatorCode;
import uk.ac.imperial.lsds.seep.operator.compose.micro.IMicroOperatorConnectable;
import uk.ac.imperial.lsds.seep.operator.compose.multi.MultiOperator;
import uk.ac.imperial.lsds.seep.operator.compose.subquery.ISubQueryConnectable;
import uk.ac.imperial.lsds.seep.operator.compose.window.IWindowDefinition;
import uk.ac.imperial.lsds.seep.operator.compose.window.WindowDefinition;
import uk.ac.imperial.lsds.seep.operator.compose.window.WindowDefinition.WindowType;
import uk.ac.imperial.lsds.streamsql.conversion.DoubleConversion;
import uk.ac.imperial.lsds.streamsql.expressions.ColumnReference;
import uk.ac.imperial.lsds.streamsql.expressions.ValueExpression;
import uk.ac.imperial.lsds.streamsql.op.stateful.MicroAggregation;
import uk.ac.imperial.lsds.streamsql.op.stateless.Projection;
import uk.ac.imperial.lsds.streamsql.op.stateless.Selection;
import uk.ac.imperial.lsds.streamsql.predicates.ComparisonPredicate;


public class LBRQ4 {

	private MultiOperator mo;
	
	private API api;
	
	public void setup(API api) {
		this.api = api;
		
		/*
		 * Definition of schemas for streams 
		 */
		List<String> posSpeedStr = new ArrayList<String>();
		posSpeedStr.add("vehicleId");
		posSpeedStr.add("speed");
		posSpeedStr.add("xPos");
		posSpeedStr.add("dir");
		posSpeedStr.add("hwy");
		
		List<String> segSpeedStr = new ArrayList<String>();
		segSpeedStr.add("vehicleId");
		segSpeedStr.add("speed");
		segSpeedStr.add("segNo");
		segSpeedStr.add("dir");
		segSpeedStr.add("hwy");

		/*
		 * Query 1
		 * 
		 * Select vehicleId, speed, xPos/5280 as segNo, dir, hwy
		 * From PosSpeedStr
		 */
//		List<IValueExpression> projExpressions = new ArrayList<>();
//		projExpressions.add(new ColumnReference<>(new StringConversion(), "vehicleId"));
//		projExpressions.add(new ColumnReference<>(new StringConversion(), "speed"));
//		IValueExpression ex = new Division(new ColumnReference<>(new DoubleConversion(), "xPos"), new ValueExpression<>(new IntegerConversion(), 5280));
//		projExpressions.add(ex);
//		projExpressions.add(new ColumnReference<>(new StringConversion(), "dir"));
//		projExpressions.add(new ColumnReference<>(new StringConversion(), "hwy"));
//		
//		IMicroOperatorCode q1ProjCode = new Projection(projExpressions, segSpeedStr);
//		IMicroOperatorConnectable q1Proj = QueryBuilder.newMicroOperator(q1ProjCode, 1);
//		
//		Set<IMicroOperatorConnectable> q1MicroOps = new HashSet<>();
//		q1MicroOps.add(q1Proj);
//
		Map<Integer, IWindowDefinition> windowDefs = new HashMap<>();
//		windowDefs.put(11, new WindowDefinition(WindowType.ROW_BASED, 1, 1));
//		ISubQueryConnectable sq1 = QueryBuilder.newSubQuery(q1MicroOps, 2, windowDefs);

		
		/*
		 * Query 4
		 * 
		 * Select segNo, dir, hwy
		 * From SegSpeedStr [Range 5 Minutes]
		 * Group By segNo, dir, hwy
		 * Having Avg(speed) < 40
		 */
		IMicroOperatorCode q2AggCode = new MicroAggregation(MicroAggregation.AggregationType.AVG, "speed", new String[] {"position", "direction", "highway"});
		IMicroOperatorConnectable q2Agg = QueryBuilder.newMicroOperator(q2AggCode, 3);

		IMicroOperatorCode q2SelCode = new Selection(new ComparisonPredicate(
				ComparisonPredicate.LESS_OP, 
				new ColumnReference<>(new DoubleConversion(), "AVG(speed)"), 
				new ValueExpression<>(new DoubleConversion(), 40.0)));
		IMicroOperatorConnectable q2Sel = QueryBuilder.newMicroOperator(q2SelCode, 4);

		IMicroOperatorCode q2ProjCode = new Projection(new String[] {"position", "direction", "highway"});
		IMicroOperatorConnectable q2Proj = QueryBuilder.newMicroOperator(q2ProjCode, 2);

		
		q2Agg.connectTo(1, q2Sel);
		q2Sel.connectTo(2, q2Proj);

		Set<IMicroOperatorConnectable> q2MicroOps = new HashSet<>();
		q2MicroOps.add(q2Proj);
		q2MicroOps.add(q2Agg);
		q2MicroOps.add(q2Sel);

		windowDefs = new HashMap<>();
		windowDefs.put(12, new WindowDefinition(WindowType.RANGE_BASED, 300, 1));
//		windowDefs.put(12, new WindowDefinition(WindowType.RANGE_BASED, 2, 1));
		ISubQueryConnectable sq2 = QueryBuilder.newSubQuery(q2MicroOps, 5, windowDefs);

//		sq1.connectTo(sq2, 101);

		Set<ISubQueryConnectable> subQueries = new HashSet<>();
//		subQueries.add(sq1);
		subQueries.add(sq2);
		
//		Connectable multiOp = QueryBuilder.newMultiOperator(subQueries, 100, posSpeedStr);

		this.mo = MultiOperator.synthesizeFrom(subQueries, 100);
		this.mo.setUp();

	}
	
	public void process(DataTuple tuple) {
		this.mo.processData(tuple, this.api);
	}
	
}
