package org.ggp.base.player.gamer.statemachine.sample;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.glados.GladosCompiledPropNetStateMachine;

import java.util.List;

/**
 * MySpeedTester tries to compute how many states/second
 * your StateMachine can explore in normal circumstances. 
 *
 */
public final class MySpeedTester extends SampleGamer
{
    // Replace with your own implementation
    public StateMachine getInitialStateMachine() {
        return new CachedStateMachine(new GladosCompiledPropNetStateMachine());
    }

    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        System.out.println("Metagame. Speed benchmark started.");
        StateMachine stateMachine = getStateMachine();
        MachineState rootState = stateMachine.getInitialState();

        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        int nbExpansion = 0;

        while(System.currentTimeMillis() < finishBy){
            MachineState state = rootState;

            while(true){
                boolean isTerminal = stateMachine.isTerminal(state);
                if(isTerminal){
                    List goal = stateMachine.getGoals(state);
                    break;
                }

                List jointMove = stateMachine.getRandomJointMove(state);

                MachineState nextState = stateMachine.getNextState(state, jointMove);
                state = nextState;

                nbExpansion++;
            }
        }

        long end = System.currentTimeMillis();
        System.out.println("Metagaming finished");
        System.out.println("Nb expansion/second : " + 1000*nbExpansion/(end-start));
    }

    /**
     * A legal gamer
     */
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();

        List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
        Move selection = moves.get(0);
        long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }

}