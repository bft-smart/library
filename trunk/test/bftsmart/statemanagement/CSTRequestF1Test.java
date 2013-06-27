package bftsmart.statemanagement;

import static org.junit.Assert.*;

import org.junit.Test;

import bftsmart.statemanagement.strategy.durability.CSTRequestF1;

public class CSTRequestF1Test {

	
	@Test
	public void testDefineReplicas() {

		// replica 3 was the last to take the checkpoint
		CSTRequestF1 request = new CSTRequestF1(11700);
		int[] otherProcesses = {1,2,3};
		request.defineReplicas(otherProcesses, 10000, 0);
		assertEquals("Replica 2 should send the checkpoint", 2, request.getCheckpointReplica());
		assertEquals("Replica 1 should send the lower half of the log", 1, request.getLogLower());
		assertEquals("Replica 3 should send the upper half of the log", 3, request.getLogUpper());
		assertEquals("Upper log size should be 1701", 1701, request.getLogUpperSize());
		
		request = new CSTRequestF1(11700);
		otherProcesses[0] = 0; // 0,2,3
		request.defineReplicas(otherProcesses, 10000, 1);
		assertEquals("Replica 2 should send the checkpoint", 2, request.getCheckpointReplica());
		assertEquals("Replica 0 should send the lower half of the log", 0, request.getLogLower());
		assertEquals("Replica 3 should send the upper half of the log", 3, request.getLogUpper());
		assertEquals("Upper log size should be 1701", 1701, request.getLogUpperSize());
		
		request = new CSTRequestF1(11700);
		otherProcesses[1] = 1; // 0,1,3
		request.defineReplicas(otherProcesses, 10000, 2);
		assertEquals("Replica 1 should send the checkpoint", 1, request.getCheckpointReplica());
		assertEquals("Replica 0 should send the lower half of the log", 0, request.getLogLower());
		assertEquals("Replica 3 should send the upper half of the log", 3, request.getLogUpper());
		assertEquals("Upper log size should be 1701", 1701, request.getLogUpperSize());
		
		request = new CSTRequestF1(11700);
		otherProcesses[2] = 2; // 0,1,2
		request.defineReplicas(otherProcesses, 10000, 3);
		assertEquals("Replica 1 should send the checkpoint", 1, request.getCheckpointReplica());
		assertEquals("Replica 0 should send the lower half of the log", 0, request.getLogLower());
		assertEquals("Replica 2 should send the upper half of the log", 2, request.getLogUpper());
		assertEquals("Upper log size should be 1701", 1701, request.getLogUpperSize());
		
		// replica 0 was the last to take the checkpoint
		request = new CSTRequestF1(13300);
		otherProcesses[0] = 1; // 1,2,3
		otherProcesses[1] = 2;
		otherProcesses[2] = 3;
		request.defineReplicas(otherProcesses, 10000, 0);
		assertEquals("Replica 2 should send the checkpoint", 2, request.getCheckpointReplica());
		assertEquals("Replica 1 should send the lower half of the log", 1, request.getLogLower());
		assertEquals("Replica 3 should send the upper half of the log", 3, request.getLogUpper());
		assertEquals("Upper log size should be 801", 801, request.getLogUpperSize());
		
		request = new CSTRequestF1(13300);
		otherProcesses[0] = 0; // 0,2,3
		request.defineReplicas(otherProcesses, 10000, 1);
		assertEquals("Replica 3 should send the checkpoint", 3, request.getCheckpointReplica());
		assertEquals("Replica 2 should send the lower half of the log", 2, request.getLogLower());
		assertEquals("Replica 0 should send the upper half of the log", 0, request.getLogUpper());
		assertEquals("Upper log size should be 801", 801, request.getLogUpperSize());
		
		request = new CSTRequestF1(13300);
		otherProcesses[1] = 1; // 0,1,3
		request.defineReplicas(otherProcesses, 10000, 2);
		assertEquals("Replica 3 should send the checkpoint", 3, request.getCheckpointReplica());
		assertEquals("Replica 1 should send the lower half of the log", 1, request.getLogLower());
		assertEquals("Replica 0 should send the upper half of the log", 0, request.getLogUpper());
		assertEquals("Upper log size should be 801", 801, request.getLogUpperSize());
		
		request = new CSTRequestF1(13300);
		otherProcesses[2] = 2; // 0,1,2
		request.defineReplicas(otherProcesses, 10000, 3);
		assertEquals("Replica 2 should send the checkpoint", 2, request.getCheckpointReplica());
		assertEquals("Replica 1 should send the lower half of the log", 1, request.getLogLower());
		assertEquals("Replica 0 should send the upper half of the log", 0, request.getLogUpper());
		assertEquals("Upper log size should be 801", 801, request.getLogUpperSize());

		// replica 1 was the last to take the checkpoint
		request = new CSTRequestF1(16200);
		otherProcesses[0] = 1; // 1,2,3
		otherProcesses[1] = 2;
		otherProcesses[2] = 3;
		request.defineReplicas(otherProcesses, 10000, 0);
		assertEquals("Replica 3 should send the checkpoint", 3, request.getCheckpointReplica());
		assertEquals("Replica 2 should send the lower half of the log", 2, request.getLogLower());
		assertEquals("Replica 1 should send the upper half of the log", 1, request.getLogUpper());
		assertEquals("Upper log size should be 1201", 1201, request.getLogUpperSize());
		
		request = new CSTRequestF1(16200);
		otherProcesses[0] = 0; // 0,2,3
		request.defineReplicas(otherProcesses, 10000, 1);
		assertEquals("Replica 3 should send the checkpoint", 3, request.getCheckpointReplica());
		assertEquals("Replica 2 should send the lower half of the log", 2, request.getLogLower());
		assertEquals("Replica 0 should send the upper half of the log", 0, request.getLogUpper());
		assertEquals("Upper log size should be 1201", 1201, request.getLogUpperSize());
		
		request = new CSTRequestF1(16200);
		otherProcesses[1] = 1; // 0,1,3
		request.defineReplicas(otherProcesses, 10000, 2);
		assertEquals("Replica 0 should send the checkpoint", 0, request.getCheckpointReplica());
		assertEquals("Replica 3 should send the lower half of the log", 3, request.getLogLower());
		assertEquals("Replica 1 should send the upper half of the log", 1, request.getLogUpper());
		assertEquals("Upper log size should be 1201", 1201, request.getLogUpperSize());
		
		request = new CSTRequestF1(16200);
		otherProcesses[2] = 2; // 0,1,2
		request.defineReplicas(otherProcesses, 10000, 3);
		assertEquals("Replica 0 should send the checkpoint", 0, request.getCheckpointReplica());
		assertEquals("Replica 2 should send the lower half of the log", 2, request.getLogLower());
		assertEquals("Replica 1 should send the upper half of the log", 1, request.getLogUpper());
		assertEquals("Upper log size should be 1201", 1201, request.getLogUpperSize());
	
		// replica 2 was the last to take the checkpoint
		request = new CSTRequestF1(17700);
		otherProcesses[0] = 1; // 1,2,3
		otherProcesses[1] = 2;
		otherProcesses[2] = 3;
		request.defineReplicas(otherProcesses, 10000, 0);
		assertEquals("Replica 1 should send the checkpoint", 1, request.getCheckpointReplica());
		assertEquals("Replica 3 should send the lower half of the log", 3, request.getLogLower());
		assertEquals("Replica 2 should send the upper half of the log", 2, request.getLogUpper());
		assertEquals("Upper log size should be 201", 201, request.getLogUpperSize());
		
		request = new CSTRequestF1(17700);
		otherProcesses[0] = 0; // 0,2,3
		request.defineReplicas(otherProcesses, 10000, 1);
		assertEquals("Replica 0 should send the checkpoint", 0, request.getCheckpointReplica());
		assertEquals("Replica 3 should send the lower half of the log", 3, request.getLogLower());
		assertEquals("Replica 2 should send the upper half of the log", 2, request.getLogUpper());
		assertEquals("Upper log size should be 201", 201, request.getLogUpperSize());
		
		request = new CSTRequestF1(17700);
		otherProcesses[1] = 1; // 0,1,3
		request.defineReplicas(otherProcesses, 10000, 2);
		assertEquals("Replica 0 should send the checkpoint", 0, request.getCheckpointReplica());
		assertEquals("Replica 3 should send the lower half of the log", 3, request.getLogLower());
		assertEquals("Replica 1 should send the upper half of the log", 1, request.getLogUpper());
		assertEquals("Upper log size should be 201", 201, request.getLogUpperSize());
		
		request = new CSTRequestF1(17700);
		otherProcesses[2] = 2; // 0,1,2
		request.defineReplicas(otherProcesses, 10000, 3);
		assertEquals("Replica 1 should send the checkpoint", 1, request.getCheckpointReplica());
		assertEquals("Replica 0 should send the lower half of the log", 0, request.getLogLower());
		assertEquals("Replica 2 should send the upper half of the log", 2, request.getLogUpper());
		assertEquals("Upper log size should be 201", 201, request.getLogUpperSize());
	}
}
