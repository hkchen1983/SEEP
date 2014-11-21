package uk.ac.imperial.lsds.streamsql.expressions.eint;

import uk.ac.imperial.lsds.seep.multi.IQueryBuffer;
import uk.ac.imperial.lsds.seep.multi.TupleSchema;
import uk.ac.imperial.lsds.streamsql.expressions.ExpressionsUtil;
import uk.ac.imperial.lsds.streamsql.visitors.ValueExpressionVisitor;

public class IntAddition implements IntExpression {
	
	private IntExpression[] expressions = null;

	public IntAddition(IntExpression[] expressions) {
		this.expressions = expressions;
	}

	@Override
	public void accept(ValueExpressionVisitor vev) {
		vev.visit(this);
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < expressions.length; i++) {
			sb.append("(").append(expressions[i]).append(")");
			if (i != expressions.length - 1)
				sb.append(" + ");
		}
		return sb.toString();
	}

	@Override
	public int eval(IQueryBuffer buffer, TupleSchema schema, int offset) {
		int result = this.expressions[0].eval(buffer, schema, offset);
		for (int i = 1; i < expressions.length; i++) {
			result += expressions[i].eval(buffer, schema, offset);
		}
		return result;
	}
	
	public byte[] evalAsByte(IQueryBuffer buffer, TupleSchema schema, int offset) {
		return ExpressionsUtil.intToByteArray(eval(buffer, schema, offset));
	}

}