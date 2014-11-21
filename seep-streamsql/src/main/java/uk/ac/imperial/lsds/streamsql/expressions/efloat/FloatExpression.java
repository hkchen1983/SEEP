package uk.ac.imperial.lsds.streamsql.expressions.efloat;

import uk.ac.imperial.lsds.seep.multi.IQueryBuffer;
import uk.ac.imperial.lsds.seep.multi.TupleSchema;
import uk.ac.imperial.lsds.streamsql.expressions.Expression;
import uk.ac.imperial.lsds.streamsql.visitors.ValueExpressionVisitor;

public interface FloatExpression extends Expression {
	
	public void accept(ValueExpressionVisitor vev);

	public float eval(IQueryBuffer buffer, TupleSchema schema, int offset);
	
}