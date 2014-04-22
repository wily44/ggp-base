package org.ggp.base.player.gamer.statemachine.glados;

import java.util.ArrayList;
import java.util.List;

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
	private List<Move> optPlan1P;
	private StateMachine game = getStateMachine();
	private int maxScore;

	// Computes the max score achievable from a certain state in a single player game.
	// Works by recursively computing the max score for each state, max score = biggest score among
	// the possible states following this one
	private int maxScoreCompDelib(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine game = getStateMachine();
		if (game.isTerminal(state))
			return game.getGoal(state, role);
		List<Move> actions = game.getLegalMoves(state, role);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			int result = maxScoreCompDelib(role, game.getNextState(state, currAction));
			score = Math.max(result, score);
		}
		return score;
	}

	// Devises the best possible plan for winning a 1P game. Will timeout on games with many possible
	// states
	private List<Move> bestPlanCompDelib(Role role, MachineState state, List<Move> currSteps) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		StateMachine game = getStateMachine();
		if (game.isTerminal(state))
			return currSteps;
		List<Move> actions = game.getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			if (maxScoreCompDelib(role, game.getNextState(state, currAction)) == maxScore) {
				currSteps.add(actions.get(i));
				return bestPlanCompDelib(role, game.getNextState(state, currAction), currSteps);
			}
		}
		return null;
	}

	private int maxScoreABPrune(Role role, MachineState state, int alpha, int beta) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		StateMachine game = getStateMachine();
		if (game.isTerminal(state)) {
			return game.getGoal(state, role);
		}
		List<Move> actions = game.getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			int result = minScoreABPrune(role, actions.get(i), state, alpha, beta);
			alpha = Math.max(alpha, result);
			if (alpha >= beta)
				return beta;
		}
		return alpha;
	}

	private int minScoreABPrune(Role role, Move ourAction, MachineState state, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		StateMachine game = getStateMachine();
		List<List<Move>> actions = game.getLegalJointMoves(state, role, ourAction);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currActions = actions.get(i);
			MachineState newstate = game.getNextState(state, currActions);
			int result = maxScoreABPrune(role, newstate, alpha, beta);
			beta = Math.min(beta, result);
			if (beta <= alpha)
				return alpha;
		}
		return beta;
	}

	private Move bestMoveABPrune(Role role, MachineState state, int alpha, int beta) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		StateMachine game = getStateMachine();
		List<Move> actions = game.getLegalMoves(state, role);
		Move chosen = actions.get(0);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			int result = minScoreABPrune(role, actions.get(i), state, alpha, beta);
			if (result == beta)
				return actions.get(i);
			if (result > score) {
				score = result;
				chosen = actions.get(i);
			}
		}
		return chosen;
	}


	@Override
	public String getName() {
		return "GLaDOS";
	}

	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long start = System.currentTimeMillis();

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = getStateMachine().getRandomMove(getCurrentState(), getRole());
		if (getStateMachine().getRoles().size() == 1) {
			selection = optPlan1P.remove(0);
		} else {
			selection = bestMoveABPrune(getRole(), getStateMachine().getInitialState(), 0, 100);
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
		if (getStateMachine().getRoles().size() == 1) {
			List<Move> currList = new ArrayList<Move>();
			maxScore = maxScoreCompDelib(getRole(), getStateMachine().getInitialState());
			optPlan1P = bestPlanCompDelib(getRole(), getStateMachine().getInitialState(), currList);
		} else {
			maxScore = maxScoreABPrune(getRole(), getStateMachine().getInitialState(), 0, 100);
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