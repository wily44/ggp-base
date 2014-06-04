package org.ggp.base.player.gamer.statemachine.glados;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.glados.GladosCompiledPropNetStateMachine;

/**
 * Created on 5/24/14.
 */
public class ShinGladosGamer extends StateMachineGamer {

	private static final int NUM_PROBES = 10;
	private static final int TERMNODE_ATTEMPTS = 10000;
	public static final Random RANDOM_GEN = new Random();

	private StateMachine game;
	private int ourIndex;
//	private MCTSNodeSP rootSP;
	private HashMap<MachineState, MCTSNodeSP> MachineStateMapSP;

	private static int counter = 0;

	public class MCTSNodeSP
	{
		public MCTSNodeSP(MCTSNodeSP parent, MachineState state, int index) throws MoveDefinitionException {
			this.state = state;
			this.parent = parent;
			this.index = index;
			numChildren = game.getLegalMoves(state, getRole()).size();
			totalAttempts = 0;
			moveUtility = new double[numChildren];
			moveAttempts = new long[numChildren];
			children = new ArrayList<MCTSNodeSP>();
		}

		public double getValue(int moveIndex) {
			return moveUtility[moveIndex] + Math.sqrt(2 * Math.log(totalAttempts / moveAttempts[moveIndex]));
		}

		public double getBestScore() {
			double bestscore = 0;
			for (int i = 0; i < numChildren; i++) {
				if (moveUtility[i] / moveAttempts[i] > bestscore) bestscore = moveUtility[i] / moveAttempts[i];
			}
			return bestscore;
		}


		public int getBestIndex() {
			int index = RANDOM_GEN.nextInt(numChildren);
			double bestscore = 0;
			for (int i = 0; i < numChildren; i++) {
				if (moveUtility[i] / moveAttempts[i] > bestscore)  {
					bestscore = moveUtility[i] / moveAttempts[i];
					index = i;
				}
			}
			return index;
		}

		public boolean isExpanded() {
			return numChildren == children.size();
		}

		public boolean isTerminal() {
			return game.isTerminal(state);
		}


		public MachineState state;
		public MCTSNodeSP parent;
		public int index;
		public int numChildren;
		public long totalAttempts;
		public double[] moveUtility;
		public long[] moveAttempts;
		public List<MCTSNodeSP> children;
	}

	private MCTSNodeSP selectionSP(MCTSNodeSP node) throws MoveDefinitionException, TransitionDefinitionException {
		if (node.totalAttempts == 0 || node.isTerminal()) return node;
		if (!node.isExpanded()) return expandSP(node);
		double bestValue = 0;
		MCTSNodeSP result = node;
		for (int i = 0; i < node.numChildren; i++) {
			if (node.getValue(i) > bestValue) {
				bestValue = node.getValue(i);
				result = node.children.get(i);
			}
		}
	//	System.out.println("INFLOOPHERE");
		return result;
	}

	private MCTSNodeSP expandSP(MCTSNodeSP node) throws MoveDefinitionException, TransitionDefinitionException {
		int index = node.children.size();
		Move theMove = game.getLegalMoves(node.state, getRole()).get(index);
		List<Move> allMoves = new ArrayList<Move>();
		allMoves.add(theMove);
		MachineState newstate = game.getNextState(node.state, allMoves);
		MCTSNodeSP newnode = new MCTSNodeSP(node, newstate, index);
		MachineStateMapSP.put(newnode.state, newnode);
		node.children.add(newnode);
		return newnode;
	}

	private void updateTreeSP(long timeout)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		while (System.currentTimeMillis() < timeout) {
			MCTSNodeSP currnode = MachineStateMapSP.get(getCurrentState());
			if(currnode == null) { // make a new root node!
                //MachineStateMapMP = new HashMap<>();
				System.out.println("MISS !!!");
                MachineStateMapSP.put(getCurrentState(), new MCTSNodeSP(null, getCurrentState(), 0));
            }
            currnode = MachineStateMapSP.get(getCurrentState());
			while (currnode.isExpanded() && !currnode.isTerminal()) {
				currnode = selectionSP(currnode);
			}
			currnode = selectionSP(currnode);
			updateScores(currnode);
		}
	}

	private void updateScores(MCTSNodeSP node) throws MoveDefinitionException, GoalDefinitionException {
		if (node.isTerminal()) {
			node.totalAttempts += TERMNODE_ATTEMPTS;
			backpropagateSP(node.parent, TERMNODE_ATTEMPTS, TERMNODE_ATTEMPTS * game.getGoal(node.state, getRole()), node.index);
		} else {
			node.totalAttempts += node.numChildren;
			for (int i = 0; i < node.numChildren; i++) {
				double scoreIncrease = 0;
				for (int j = 0; j < NUM_PROBES; j++) {
					scoreIncrease += performDepthChargeFromMove(node.state, game.getLegalMoves(node.state, getRole()).get(i));
				}
				node.moveUtility[i] += scoreIncrease / NUM_PROBES;
				node.moveAttempts[i] += 1;
			}
			if (node.parent != null) {
				backpropagateSP(node.parent, node.numChildren, node.getBestScore() * node.numChildren, node.index);
			}
		}
	}

	private void backpropagateSP(MCTSNodeSP node, int attempts, double score, int index) {
		node.moveUtility[index] += score;
		node.moveAttempts[index] += attempts;
		node.totalAttempts += attempts;
		if (node.parent != null) backpropagateSP(node.parent, attempts, score, node.index);
	}



	@Override
	public StateMachine getInitialStateMachine() {
        return new CachedStateMachine(new GladosCompiledPropNetStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		long finishBy = timeout - 2000;
		game = getStateMachine();
		ourIndex = game.getRoleIndices().get(getRole());
		if(game.getRoles().size() == 1) {
            MCTSNodeSP rootSP = new MCTSNodeSP(null, game.getInitialState(), 0);
            MachineStateMapSP = new HashMap<MachineState, MCTSNodeSP>();
            MachineStateMapSP.put(game.getInitialState(), rootSP);
            updateTreeSP(finishBy);
        }
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException,
			GoalDefinitionException {
		long start = System.currentTimeMillis();
        long finishBy = timeout - 2000;

        // One player game
        List<Move> moves = game.getLegalMoves(getCurrentState(), getRole());
        Move selection = moves.get(0);
        if (game.getRoles().size() == 1) {
        	updateTreeSP(finishBy);
        	MCTSNodeSP currnode = MachineStateMapSP.get(getCurrentState());
        	return moves.get(currnode.getBestIndex());
        } else {
        	long[] moveTotalPoints = new long[moves.size()];
    		long[] moveTotalAttempts = new long[moves.size()];

    		// Perform depth charges for each candidate move, and keep track
    		// of the total score and total attempts accumulated for each move.
    		for (int i = 0; true; i = (i+1) % moves.size()) {
    		    if (System.currentTimeMillis() > finishBy)
    		        break;

    		    int theScore = performDepthChargeFromMove(getCurrentState(), moves.get(i));
    		    moveTotalPoints[i] += theScore;
    		    moveTotalAttempts[i] += 1;
    		}
    		// Compute the expected score for each move.
    		double[] moveExpectedPoints = new double[moves.size()];
    		for (int i = 0; i < moves.size(); i++) {
    		    moveExpectedPoints[i] = (double)moveTotalPoints[i] / moveTotalAttempts[i];
    		}

    		// Find the move with the best expected score.
    		int bestMove = 0;
    		double bestMoveScore = moveExpectedPoints[0];
    		for (int i = 1; i < moves.size(); i++) {
    		    if (moveExpectedPoints[i] > bestMoveScore) {
    		        bestMoveScore = moveExpectedPoints[i];
    		        bestMove = i;
    		    }
    		}
    		selection = moves.get(bestMove);
        }

        long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
	}

	@Override
	public void stateMachineStop() {

	}

	@Override
	public void stateMachineAbort() {

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {

	}

    @Override
    public DetailPanel getDetailPanel() {
        return new SimpleDetailPanel();
    }

	@Override
	public String getName() {
		return "ShinGlados";
	}

	private int[] depth = new int[1];
	int performDepthChargeFromMove(MachineState theState, Move myMove) {
	    try {
            MachineState finalState = game.performDepthCharge(game.getRandomNextState(theState, getRole(), myMove), depth);
            return game.getGoal(finalState, getRole());
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
	}
}