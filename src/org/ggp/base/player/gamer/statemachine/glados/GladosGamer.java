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
	private List<Move> optPlan;

	private int maxScoreSingle(Role role, MachineState state) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		// Do nothing
		StateMachine game = getStateMachine();
		if (game.isTerminal(state))
			return game.getGoal(state, role);
		List<Move> actions = game.getLegalMoves(state, role);
		int score = 0;
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			int result = maxScoreSingle(role, game.getNextState(state, currAction));
			if (result > score)
				score = result;
		}
		return score;
	}

	private List<Move> bestPlan(Role role, MachineState state, List<Move> currSteps) throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		StateMachine game = getStateMachine();
		if (game.isTerminal(state))
			return currSteps;
		List<Move> actions = game.getLegalMoves(state, role);
		int score = maxScoreSingle(role, state);
		for (int i = 0; i < actions.size(); i++) {
			List<Move> currAction = new ArrayList<Move>();
			currAction.add(actions.get(i));
			if (maxScoreSingle(role, game.getNextState(state, currAction)) == score) {
				currSteps.add(actions.get(i));
				return bestPlan(role, game.getNextState(state, currAction), currSteps);
			}
		}
		return null;
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
		Move selection = optPlan.remove(0);

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
		List<Move> currList = new ArrayList<Move>();
		optPlan = bestPlan(getRole(), getStateMachine().getInitialState(), currList);
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