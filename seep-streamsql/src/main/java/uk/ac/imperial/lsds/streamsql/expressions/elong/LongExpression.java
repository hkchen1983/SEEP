package uk.ac.imperial.lsds.streamsql.expressions.elong;

import uk.ac.imperial.lsds.seep.multi.IQueryBuffer;
import uk.ac.imperial.lsds.seep.multi.TupleSchema;
import uk.ac.imperial.lsds.streamsql.expressions.Expression;
import uk.ac.imperial.lsds.streamsql.visitors.ValueExpressionVisitor;

public interface LongExpression extends Expression {
	
	public void accept(ValueExpressionVisitor vev);

	public long eval(IQueryBuffer buffer, TupleSchema schema, int offset);
	
}