package org.ggp.base.player.gamer.statemachine.glados;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;


public final class GladosGamer extends StateMachineGamer
{
	private List<Move> optPlanSP;
	private StateMachine game;
	private int maxScore;
	private boolean sequentialPlanExists;
	private boolean maxScoreSPFinished;
	private boolean iterativeSearchFinished;
	private int ourIndex;
	private MCTSNode root;
	private HashMap<MachineState, MCTSNode> MachineStateMap;
//	private boolean selectCompleted;

	public class MCTSNode
	{
		public MCTSNode(MCTSNode parent, MachineState state) {
			this.parent = parent;
			this.state = state;
			visits = 0;
			utility = 0;
			children = new ArrayList<MCTSNode>();
		}

		public double getValue() {
			return (utility + Math.sqrt(2 * Math.log(parent.visits / visits)));
		}

		public MachineState state;
		public int visits;
		public double utility;
		public MCTSNode parent;
		public ArrayList<MCTSNode> children;
	}

	public class MCTSNodeMP
	{
		public MCTSNodeMP(MCTSNodeMP parent, MachineState state) {
			this.parent = parent;
			this.state = state;
			int numplayers = game.getRoles().size();
			utility = new ArrayList<Double>();
			visits = new ArrayList<Integer>();
			for (int i = 0; i < numplayers; i++) {
				utility.add(0.0);
				visits.add(0);
			}
			children = new ArrayList<MCTSNodeMP>();
		}

		public double getValue(int playerIndex) {
			return (utility.get(playerIndex) + Math.sqrt(2 * Math.log(this.parent.visits.get(playerIndex) / visits.get(playerIndex))));
		}

		public MachineState state;
		public MCTSNodeMP parent;
		public ArrayList<Double> utility;
		public ArrayList<Integer> visits;
		public int numplayers;
		public ArrayList<MCTSNodeMP> children;
	}


	private MCTSNodeMP selectNode(MCTSNodeMP node) {
		if (node.visits.get(ourIndex) == 0 || game.isTerminal(node.state)) {
			return node;
		}
		for (int i = 0; i < node.children.size(); i++) {
			if (node.children.get(i).visits.get(ourIndex) == 0) return node.children.get(i);
		}
		double score = 0;
		MCTSNodeMP result = node;
		for (int i = 0; i < node.children.size(); i++) {
			double newscore = node.children.get(i).getValue(ourIndex);
			if (newscore > score) {
				score = newscore;
				result = node.children.get(i);
			}
		}
		return result;
	}

	private MCTSNode selection(MCTSNode node) {
		if (node.visits == 0 || game.isTerminal(node.state)) return node;
		for (int i = 0; i < node.children.size(); i++) {
			if (node.children.get(i).visits == 0) return node.children.get(i);
		}
		double score = 0;
		MCTSNode result = node;
		for (int i = 0; i < node.children.size(); i++) {
			double newscore = node.children.get(i).getValue();
			if (newscore > score) {
				score = newscore;
				result = node.children.get(i);
			}
		}
		return result;
	}



	private boolean expandSP(MCTSNode node) throws MoveDefinitionException, TransitionDefinitionException {
		if (game.isTerminal(node.state)) return true;
		if (node.visits > 0) return false;
		List<Move> actions = game.getLegalMoves(node.state, getRole());
	//	if (node.parent != null && MachineStateMap.containsKey(node.state)) return true;
		MachineStateMap.put(node.state, node);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> move = new ArrayList<Move>();
			move.add(actions.get(i));
			MachineState newstate = game.getNextState(node.state, move);
			if (!MachineStateMap.containsKey(newstate)) {
				MCTSNode newnode = new MCTSNode(node, newstate);
				node.children.add(newnode);
			}
		}
		return true;
	}

	private void updateTreeSP(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		while (System.currentTimeMillis() < timeout) {
			MCTSNode currnode = MachineStateMap.get(getCurrentState());
			while (!expandSP(currnode) && System.currentTimeMillis() < timeout) {
				currnode = selection(currnode);
			}
			int numprobes = 1;
			if (!game.isTerminal(currnode.state)) {
				numprobes = game.getLegalMoves(currnode.state, getRole()).size();
			}
			double score = monteCarlo(getRole(), currnode.state, numprobes * 20);
			backpropagateSP(currnode, score);
		}
	}

/*
	private void updateTree(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
	//	selectCompleted = true;
		while (System.currentTimeMillis() < timeout) {
			MCTSNode currnode = selectNode(MachineStateMap.get(getCurrentState()), timeout);
		// /*   if (selectCompleted) expandSP(currnode);
		 //   else break;
			if (expandSP(currnode)) {
				int score = depthCharge(getRole(), currnode.state, timeout);
				backpropagateSP(currnode, score);
			}
		}
	} */

    private boolean backpropagateSP(MCTSNode node, double score) {
    	node.visits += 1;
    	node.utility += score;
    	MachineStateMap.put(node.state, node);
    	if (node.parent != null) backpropagateSP(node.parent, score) ;
    	return true;
    }

    private int depthCharge(Role role, MachineState state, long timeout) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
    	if (game.isTerminal(state) || System.currentTimeMillis() > timeout) return game.getGoal(state, role);
    	List<Move> moves = game.getRandomJointMove(state);
    	MachineState nextstate = game.getNextState(state, moves);
    	return depthCharge(role, nextstate, timeout);
    }

    private int depthCharge(Role role, MachineState state, int level, int limit) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	if (game.isTerminal(state) || level > limit) return game.getGoal(state, role);
    	List<Move> moves = game.getRandomJointMove(state);
    	MachineState nextstate = game.getNextState(state, moves);
    	return depthCharge(role, nextstate, level + 1, limit);
    }


    private int monteCarlo(Role role, MachineState state, int count) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
    	int total = 0;
    	for (int i = 0; i < count; i++) {
    		total += depthCharge(role, state, 0, 100);
    	}
    	return total / count;
    }

    private int monteCarloTime(Role role, MachineState state, long timeRemaining) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
    	int total = 0;
    	int count = 0;
    	long timeCutoff = System.currentTimeMillis() + timeRemaining;
    	while (System.currentTimeMillis() < timeCutoff) {
    		total += depthCharge(role, state, timeCutoff);
    		count += 1;
    	}
    	return total / count;
    }

    private Move bestMoveMCS(Role role, MachineState state, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
    	List<Move> actions = game.getLegalMoves(state, role);
    	Move result = actions.get(0);
    	int scenarios = game.getLegalJointMoves(state).size();
    	int bestscore = 0;
    	int prevBestScore = 0;
    	int prevBestIndex = 0;
    	boolean updateHappened = false;
    	boolean losingMoveFound = false;
    	Map<Role, Integer> roleIndices = game.getRoleIndices();
    	long timeGiven = (timeout - System.currentTimeMillis()) / scenarios;
    	for (int i = 0; i < actions.size(); i++) {
    		updateHappened = false;
    		losingMoveFound = false;
    		List<List<Move>> jointActions = game.getLegalJointMoves(state, role, actions.get(i));
    		for (int j = 0; j < jointActions.size(); j++) {
    			jointActions.get(j).add(roleIndices.get(role), actions.get(i));
    			MachineState nextState = game.getNextState(state, jointActions.get(j));
    			if (game.isTerminal(nextState) && game.getGoal(nextState, role) == 0) {
    				losingMoveFound = true;
    				bestscore = prevBestScore;
    				result = actions.get(prevBestIndex);
    				break;
    			}
    			int score = monteCarloTime(role, nextState, timeGiven);
    			if (score > bestscore) {
    				bestscore = score;
    				result = actions.get(i);
    				updateHappened = true;
    			}
    		}
    		if (updateHappened && !losingMoveFound) {
    			prevBestScore = bestscore;
    			prevBestIndex = i;
    		}
    	}
    	return result;
    }

/*
	private int zeroHeuristic(Role role, MachineState state) {
		return 0;
	}

	private int goalProximityHeuristic(Role role, MachineState state) throws GoalDefinitionException {
		return game.getGoal(state, role);
	}

	private int shittyMobilityHeuristic(Role role, MachineState state) throws MoveDefinitionException {
		return game.getLegalMoves(state, role).size();
	}

	private int shittyFocusHeuristic(Role role, MachineState state) throws MoveDefinitionException {
		return 100 - game.getLegalMoves(state, role).size();
	}

	interface Heuristic {
		int heuristic(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException;
	}

	private Heuristic[] heuristicsList = new Heuristic[] {
		new Heuristic() {@Override
		public int heuristic(Role role, MachineState state) { return zeroHeuristic(role, state); } },
		new Heuristic() {@Override
		public int heuristic(Role role, MachineState state) throws GoalDefinitionException { return goalProximityHeuristic(role, state); } },
		new Heuristic() {@Override
		public int heuristic(Role role, MachineState state) throws MoveDefinitionException {return shittyMobilityHeuristic(role, state); } },
		new Heuristic() {@Override
		public int heuristic(Role role, MachineState state) throws MoveDefinitionException {return shittyFocusHeuristic(role, state); } },
	};

	private int heuristic(Role role, MachineState state, int index) throws GoalDefinitionException, MoveDefinitionException {
		return heuristicsList[index].heuristic(role, state);
	}

	// Computes the max score achievable from a certain state in a single player game.
	// Works by recursively computing the max score for each state, max score = biggest score among
	// the possible states following this one
	private int maxScoreBoundedDepthSP(Role role, MachineState state, int level, int limit, int heuristicIndex, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (game.isTerminal(state)) return game.getGoal(state, role);
		if (limit >= 0 && level >= limit) {
			return heuristic(role, state, heuristicIndex);
		}
		List<Move> actions = game.getLegalMoves(state, role);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			if (System.currentTimeMillis() > timeout) {
				maxScoreSPFinished = false;
				return score;
			}
			int result = maxScoreBoundedDepthSP(role, game.getNextState(state, currAction), level + 1, limit, heuristicIndex, timeout);
			score = Math.max(result, score);
		}
		maxScoreSPFinished = true;
		return score;
	}

	private int maxScoreIterativeDepthSP(Role role, MachineState state, int heuristicIndex, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		int i = 1;
		int score = 0;
		while (true) {
			int newscore = maxScoreBoundedDepthSP(role, state, 0, i, heuristicIndex, timeout);
			score = Math.max(score, newscore);
			if (!maxScoreSPFinished) break;
			i++;
		}
		return score;
	}

	private Move bestMoveBoundedDepthSP(Role role, MachineState state, int level, int limit, int heuristicIndex, long timeout) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = game.getLegalMoves(state, role);
		Move selection = actions.get(0);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			int newScore = maxScoreBoundedDepthSP(role, game.getNextState(state, currAction), level + 1, limit, heuristicIndex, timeout);
			if (newScore >= maxScore) {
				maxScore = newScore;
				selection = actions.get(i);
			}
		}
		return selection;
	}

	private Move bestMoveIterativeDepthSP(Role role, MachineState state, int heuristicIndex, long timeout) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = game.getLegalMoves(state, role);
		Move selection = actions.get(0);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			long currTime = System.currentTimeMillis() + 100;
			long timeRemaining = (timeout - currTime) / actions.size();
			int newScore = maxScoreIterativeDepthSP(role, game.getNextState(state, currAction), heuristicIndex, timeRemaining);
			if (newScore >= maxScore) {
				maxScore = newScore;
				selection = actions.get(i);
			}
		}
		return selection;
	}


	// Devises the best possible plan for winning a SP game. Will timeout on games with many possible
	// states
/*	private List<Move> bestPlanDepthSearchSP(Role role, MachineState state, List<Move> currSteps, int heuristicIndex, long timeout) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (game.isTerminal(state)) {
			sequentialPlanExists = true;
			return currSteps;
		}
	/*	List<Move> actions = game.getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			int newScore = maxScoreBoundedDepthSP(role, game.getNextState(state, currAction), 0, -1, 0, timeout);
			if (newScore >= maxScore) {
				maxScore = newScore;
				currSteps.add(actions.get(i));
				return bestPlanDepthSearchSP(role, game.getNextState(state, currAction), currSteps, timeout);
			}
		}

		Move bestMove = bestMoveIterativeDepthSP(role, state, 0, timeout);
		if (bestMove == null) return null;
		currSteps.add(bestMove);
		return bestPlanDepthSearchSP(role, state, currSteps, heuristicIndex, timeout);
	}


	// Function used to compute score in "max cells" when utilizing alpha-beta pruning
	private int maxScoreABPrune(Role role, MachineState state, int alpha, int beta, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (game.isTerminal(state)) {
			return game.getGoal(state, role);
		}
		List<Move> actions = game.getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			if (System.currentTimeMillis() > timeout) break;
			int result = minScoreABPrune(role, actions.get(i), state, alpha, beta, timeout);
			alpha = Math.max(alpha, result);
			if (alpha >= beta)
				return beta;
		}
		return alpha;
	}

	// Function used to compute score in "min cells" when utilizing alpha-beta pruning
	private int minScoreABPrune(Role role, Move ourAction, MachineState state, int alpha, int beta, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<List<Move>> actions = game.getLegalJointMoves(state, role, ourAction);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currActions = actions.get(i);
			MachineState newstate = game.getNextState(state, currActions);
			int result = maxScoreABPrune(role, newstate, alpha, beta, timeout);
			beta = Math.min(beta, result);
			if (beta <= alpha)
				return alpha;
		}
		return beta;
	}

	// Chooses the best action for this state & role given the current information using
	// alpha-beta pruning
	private Move bestMoveABPrune(Role role, MachineState state, int alpha, int beta, long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<Move> actions = game.getLegalMoves(state, role);
		Move chosen = actions.get(0);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			int result = minScoreABPrune(role, actions.get(i), state, alpha, beta, timeout);
			if (result == beta)
				return actions.get(i);
			if (result > score) {
				score = result;
				chosen = actions.get(i);
			}
		}
		return chosen;
	}

	private int maxScoreBoundedDepthMP(Role role, MachineState state, int level, int limit, int heuristicIndex, long timeout) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (game.isTerminal(state)) return game.getGoal(state, role);
		if (limit >= 0 && level >= limit) {
			return heuristic(role, state, heuristicIndex);
		}
		List<Move> actions = game.getLegalMoves(state, role);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			if (System.currentTimeMillis() > timeout) break;
			int result = minScoreBoundedDepthMP(role, actions.get(i), state, level, limit, heuristicIndex, timeout);
			score = Math.max(result, score);
		}
		return score;
	}

	private int minScoreBoundedDepthMP(Role role, Move ourAction, MachineState state, int level, int limit, int heuristicIndex, long timeout) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		List<List<Move>> actions = game.getLegalJointMoves(state, role, ourAction);
		int score = 100;
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currActions = actions.get(i);
			MachineState newstate = game.getNextState(state, currActions);
			int result = maxScoreBoundedDepthMP(role, newstate, level + 1, limit, heuristicIndex, timeout);
			score = Math.min(score, result);
		}
		return score;
	}

	private Move bestMoveBoundedDepthMP(Role role, MachineState state, int level, int limit, int heuristicIndex, long timeout) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = game.getLegalMoves(state, role);
		Move chosen = actions.get(0);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			int result = minScoreBoundedDepthMP(role, actions.get(i), state, level, limit, heuristicIndex, timeout);
			if (result > score) {
				score = result;
				chosen = actions.get(i);
			}
		}
		return chosen;
	} */


	@Override
	public String getName() {
		return "GLaDOS";
	}

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long start = System.currentTimeMillis();
		long finishBy = timeout - 2000;

		List<Move> moves = game.getLegalMoves(getCurrentState(), getRole());
		Move selection = moves.get(0);
		if (game.getRoles().size() == 1) {
			updateTreeSP(finishBy);
			MCTSNode currnode = MachineStateMap.get(getCurrentState());
			int index = 0;
			double bestscore = 0;
			for (int i = 0; i < currnode.children.size(); i++) {
				MCTSNode child = currnode.children.get(i);
				if ((child.utility / child.visits) > bestscore) {
					bestscore = child.utility / child.visits;
					index = i;
				}
			}
			if (bestscore == 0) {
				selection = game.getRandomMove(getCurrentState(), getRole());
				System.out.println("Random");
			}
			else selection = moves.get(index);
	//		root = root.children.get(index);
		}
	/*		if (sequentialPlanExists) {
				selection = optPlanSP.remove(0);
			} else {
				/*int bestSeenScore = 0;
				for (int i = 0; i < moves.size(); i++) {
					List<Move> currAction = new ArrayList<Move>();
					currAction.add(moves.get(i));
					int currScore = maxScoreBoundedDepthSP(getRole(), game.getNextState(getCurrentState(), currAction), 0, 10, 1, finishBy);
					if (currScore > bestSeenScore) {
						bestSeenScore = currScore;
						selection = moves.get(i);
					}
				}
				selection = bestMoveBoundedDepthSP(getRole(), getCurrentState(), 0, 8, 1, finishBy); */
		 else {
			//selection = bestMoveBoundedDepthMP(getRole(), getCurrentState(), 0, 2, 1, finishBy);
			 selection = bestMoveMCS(getRole(), getCurrentState(), finishBy);
		 }

		long stop = System.currentTimeMillis();

		notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
		return selection;
	}

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {

	}

	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long start = System.currentTimeMillis();
		long finishBy = timeout - 1500;

		game = getStateMachine();
		ourIndex = game.getRoleIndices().get(getRole());
		sequentialPlanExists = false;
		root = new MCTSNode(null, game.getInitialState());
		MachineStateMap = new HashMap<MachineState, MCTSNode>();
		MachineStateMap.put(game.getInitialState(), root);
		if (game.getRoles().size() == 1) {
			updateTreeSP(finishBy);
		}
	}

	@Override
	public void stateMachineStop() {
		// Does no special cleanup when the match ends normally.
	}

	@Override
	public void stateMachineAbort() {
		// Does no special cleanup when the match ends abruptly.
	}

	@Override
	public DetailPanel getDetailPanel() {
		return new SimpleDetailPanel();
	}
}