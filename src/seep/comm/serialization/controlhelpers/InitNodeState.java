package seep.comm.serialization.controlhelpers;

public class InitNodeState {

	private int opId;
	private InitOperatorState initOperatorState;
	//
	private int senderOperatorId;
	
	public int getSenderOperatorId() {
		return senderOperatorId;
	}
	public void setSenderOperatorId(int senderOperatorId) {
		this.senderOperatorId = senderOperatorId;
	}
	public int getOpId() {
		return opId;
	}
	public void setOpId(int opId) {
		this.opId = opId;
	}
	public InitOperatorState getInitOperatorState() {
		return initOperatorState;
	}
	public void setInitOperatorState(InitOperatorState initOperatorState) {
		this.initOperatorState = initOperatorState;
	}
	
	public InitNodeState(){	}
	
	public InitNodeState(int senderOperatorId, int nodeId, InitOperatorState initOperatorState){
		this.senderOperatorId = senderOperatorId;
		this.opId = nodeId;
		this.initOperatorState = initOperatorState;
	}
	
}
