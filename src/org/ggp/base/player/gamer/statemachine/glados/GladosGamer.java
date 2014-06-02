package org.ggp.base.player.gamer.statemachine.glados;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.glados.GladosCompiledPropNetStateMachine;
import org.ggp.base.util.statemachine.implementation.glados.GladosPropNetStateMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created on 5/24/14.
 */
public class GladosGamer extends SampleGamer {

    private static final int NUM_PROBES = 5;

    //private List<Move> optPlanSP;
    //private int maxScore;
    private boolean sequentialPlanExists;
    //private boolean maxScoreSPFinished;
    //private boolean iterativeSearchFinished;
//	private boolean selectCompleted;

    private StateMachine game;
    private int ourIndex;
    private MCTSNodeSP rootSP;
    private HashMap<MachineState, MCTSNodeSP> MachineStateMapSP;

    private MCTSNodeMP rootMP;
    private HashMap<MachineState, MCTSNodeMP> MachineStateMapMP;

    public class MCTSNodeSP
    {
        public MCTSNodeSP(MCTSNodeSP parent, MachineState state) {
            this.parent = parent;
            this.state = state;
            visits = 0;
            utility = 0;
            children = new ArrayList<MCTSNodeSP>();
        }

        public double getValue() { // I changed this?
            //return (utility + Math.sqrt(2 * Math.log(parent.visits / visits)));
            return utility / visits + Math.sqrt(2 * Math.log(parent.visits) / visits);
        }

        public MachineState state;
        public int visits;
        public double utility;
        public MCTSNodeSP parent;
        public ArrayList<MCTSNodeSP> children;
    }

    public class MCTSNodeMP
    {
        public MCTSNodeMP(MCTSNodeMP parent, MachineState state) {
            this.parent = parent;
            this.state = state;
            int numplayers = game.getRoles().size();
            utility = new ArrayList<Double>();
            //visits = new ArrayList<Integer>();
            visits = 0;
            for (int i = 0; i < numplayers; i++) {
                utility.add(0.0);
                //visits.add(0);
            }
            children = new ArrayList<ArrayList<MCTSNodeMP>>();
        }

        public double getValue(int playerIndex) { // I changed this?
            //return (utility.get(playerIndex) + Math.sqrt(2 * Math.log(this.parent.visits.get(playerIndex) / visits.get(playerIndex))));
            return utility.get(playerIndex) / visits + Math.sqrt(2 * Math.log(this.parent.visits) / visits);
        }

        public double getRealUtility(int playerIndex) {
            return utility.get(playerIndex) / visits;
        }

        public double getSelectionValue() {
            double value = 100;
            for (int i = 0; i < game.getRoles().size(); i++) {
                value *= (this.getRealUtility(i) / 100);
            }
            return value;
        }

        public MachineState state;
        public MCTSNodeMP parent;
        public ArrayList<Double> utility;
        //public ArrayList<Integer> visits;
        public int visits;
        public int numplayers;
        public ArrayList<ArrayList<MCTSNodeMP>> children;
    }


    private MCTSNodeMP selectionMP(MCTSNodeMP node) {
        //if (node.visits.get(ourIndex) == 0 || game.isTerminal(node.state)) {
        if (node.visits == 0 || game.isTerminal(node.state)) {
            return node;
        }
        for (int i = 0; i < node.children.size(); i++) {
            //if (node.children.get(i).visits.get(ourIndex) == 0) return node.children.get(i);
            for (int j = 0; j < node.children.get(i).size(); j++) {
                if (node.children.get(i).get(j).visits == 0) return node.children.get(i).get(j);
            }
        }
        double score = 0;
        MCTSNodeMP result = node;
        for (int i = 0; i < node.children.size(); i++) {
            for (int j = 0; j < node.children.get(i).size(); j++) {
                double newscore = node.children.get(i).get(j).getValue(ourIndex);
                if (newscore > score) {
                    score = newscore;
                    result = node.children.get(i).get(j);
                }
            }
        }
        return result;
    }

    private MCTSNodeSP selectionSP(MCTSNodeSP node) {
        if (node.visits == 0 || game.isTerminal(node.state)) return node;
        for (int i = 0; i < node.children.size(); i++) {
            if (node.children.get(i).visits == 0) return node.children.get(i);
        }
        double score = 0;
        MCTSNodeSP result = node;
        for (int i = 0; i < node.children.size(); i++) {
            double newscore = node.children.get(i).getValue();
            if (newscore > score) {
                score = newscore;
                result = node.children.get(i);
            }
        }
        return result;
    }



    private boolean expandSP(MCTSNodeSP node) throws MoveDefinitionException, TransitionDefinitionException {
        if (game.isTerminal(node.state)) return true;
        if (node.visits > 0) return false;
        List<Move> actions = game.getLegalMoves(node.state, getRole());
        //	if (node.parent != null && MachineStateMapSP.containsKey(node.state)) return true;
        MachineStateMapSP.put(node.state, node);
        for (int i = 0; i < actions.size(); i++) {
            List<Move> move = new ArrayList<Move>();
            move.add(actions.get(i));
            MachineState newstate = game.getNextState(node.state, move);
            if (!MachineStateMapSP.containsKey(newstate)) {
                MCTSNodeSP newnode = new MCTSNodeSP(node, newstate);
                node.children.add(newnode);
            }
        }
        return true;
    }

    private boolean expandMP(MCTSNodeMP node) throws MoveDefinitionException, TransitionDefinitionException {
        if(game.isTerminal(node.state)) return true;
        //if(node.visits > 0) return false;
        if(node.visits > 0) return false;
        //List<List<Move> > actions = game.getLegalJointMoves(node.state);
        System.out.println("ExpandedNode");
        List<Move> ourActions = game.getLegalMoves(node.state, getRole());
        MachineStateMapMP.put(node.state, node);
        for (int i = 0; i < ourActions.size(); i++) {
            ArrayList<MCTSNodeMP> newchildren = new ArrayList<MCTSNodeMP>();
            node.children.add(newchildren);
            List<List<Move>> allActions = game.getLegalJointMoves(node.state, getRole(), ourActions.get(i));
            for (int j = 0; j < allActions.size(); j++) {
                allActions.get(j).add(ourIndex, ourActions.get(i));
                MachineState newstate = game.getNextState(node.state, allActions.get(j));
                if (!MachineStateMapMP.containsKey(newstate)) {
                    MCTSNodeMP newnode = new MCTSNodeMP(node, newstate);
                    node.children.get(i).add(newnode);
                }
            }
        }
    /*    List<Move> randomJointMove = actions.get((int)(Math.random() * actions.size()));
        MachineState newstate = game.getNextState(node.state, randomJointMove);
        if(!MachineStateMapMP.containsKey(newstate)) {
            node.children.add(new MCTSNodeMP(node, newstate));
        } */
        return true;
    }

    private void updateTreeSP(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
        while (System.currentTimeMillis() < timeout) {
            MCTSNodeSP currnode = MachineStateMapSP.get(getCurrentState());
            while (!expandSP(currnode) && System.currentTimeMillis() < timeout) {
                currnode = selectionSP(currnode);
            }
            int numprobes = 1;
            if (!game.isTerminal(currnode.state)) {
                numprobes = game.getLegalMoves(currnode.state, getRole()).size();
            }
            double score = monteCarlo(getRole(), currnode.state, numprobes * NUM_PROBES);
            backpropagateSP(currnode, score);
        }
    }

    private void updateTreeMP(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
        while(System.currentTimeMillis() < timeout) {
            MCTSNodeMP currnode = MachineStateMapMP.get(getCurrentState());
            if(currnode == null) { // make a new root node!
                //MachineStateMapMP = new HashMap<>();
                MachineStateMapMP.put(getCurrentState(), new MCTSNodeMP(null, getCurrentState()));
            }
            currnode = MachineStateMapMP.get(getCurrentState());
            //System.out.println("get: " + MachineStateMapMP.get(getCurrentState()));
            while(!expandMP(currnode) && System.currentTimeMillis() < timeout) {
                currnode = selectionMP(currnode);
                System.out.println(System.currentTimeMillis() + " " + timeout + " " + (timeout - System.currentTimeMillis()));
            }
            System.out.println("Break 1");
            int numprobes = 1;
            if(!game.isTerminal(currnode.state)) {
                numprobes = game.getLegalJointMoves(currnode.state).size();
            }
            System.out.println("Break 2");
            List<Double> scores = monteCarloMP(currnode.state, numprobes * NUM_PROBES, timeout);
            System.out.println("Break 3");
            backpropagateMP(currnode, scores);
            System.out.println("UpdateTree");
        }
    }

/*
	private void updateTree(long timeout) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
	//	selectCompleted = true;
		while (System.currentTimeMillis() < timeout) {
			MCTSNodeSP currnode = selectionMP(MachineStateMapSP.get(getCurrentState()), timeout);
		// /*   if (selectCompleted) expandSP(currnode);
		 //   else break;
			if (expandSP(currnode)) {
				int score = depthCharge(getRole(), currnode.state, timeout);
				backpropagateSP(currnode, score);
			}
		}
	} */

    private boolean backpropagateSP(MCTSNodeSP node, double score) {
        node.visits += 1;
        node.utility += score;
        MachineStateMapSP.put(node.state, node);
        if (node.parent != null) backpropagateSP(node.parent, score) ;
        return true;
    }

    private boolean backpropagateMP(MCTSNodeMP node, List<Double> scores) {
        node.visits += 1;
        for(int i = 0; i < node.utility.size(); i++) {
            node.utility.set(i, node.utility.get(i) + scores.get(i));
        }
        MachineStateMapMP.put(node.state, node);
        if(node.parent != null) backpropagateMP(node.parent, scores);
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

    private ArrayList<Integer> depthChargeMP(MachineState state, int level, int limit) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
        if (game.isTerminal(state) || level > limit) {
            ArrayList<Integer> ret = new ArrayList<Integer>();
            for(Role r : game.getRoles()) {
                ret.add(game.getGoal(state, r));
            }
            return ret;
        }
        List<Move> moves = game.getRandomJointMove(state);
        MachineState nextstate = game.getNextState(state, moves);
        return depthChargeMP(nextstate, level + 1, limit);
    }


    private double monteCarlo(Role role, MachineState state, int count) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
        int total = 0;
        for (int i = 0; i < count; i++) {
            total += depthCharge(role, state, 0, 100);
        }
        return total / (double)count;
    }

    private List<Double> monteCarloMP(MachineState state, int count, long timeout) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
        List<Double> total = new ArrayList<Double>();
        for(int i = 0; i < game.getRoles().size(); i++) {
            total.add(0.);
        }
        for (int i = 0; i < count; i++) {
            if (System.currentTimeMillis() > timeout) break;
            ArrayList<Integer> scores = depthChargeMP(state, 0, 100);
            for(int j = 0; j < total.size(); j++) {
                total.set(j, total.get(j) + scores.get(j));
            }
        }
        for(int i = 0; i < total.size(); i++) {
            total.set(i, total.get(i) / (double)count);
        }
        return total;
    }

    private int monteCarloTime(Role role, MachineState state, long timeRemaining) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
        int total = 0;
        int count = 0;
        long timeCutoff = System.currentTimeMillis() + timeRemaining;
        while (System.currentTimeMillis() < timeCutoff) {
            total += depthCharge(role, state, timeCutoff);
            count++;
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

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();
        long finishBy = timeout - 3000;

        // One player game
        List<Move> moves = game.getLegalMoves(getCurrentState(), getRole());
        Move selection = moves.get(0);
        if (game.getRoles().size() == 1) {
            updateTreeSP(finishBy);
            MCTSNodeSP currnode = MachineStateMapSP.get(getCurrentState());
            int index = 0;
            double bestscore = 0;
            for (int i = 0; i < currnode.children.size(); i++) {
                MCTSNodeSP child = currnode.children.get(i);
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
            //		rootSP = rootSP.children.get(index);
        }
	/*		if (sequentialPlanExists) {
				selectionSP = optPlanSP.remove(0);
			} else {
				/*int bestSeenScore = 0;
				for (int i = 0; i < moves.size(); i++) {
					List<Move> currAction = new ArrayList<Move>();
					currAction.add(moves.get(i));
					int currScore = maxScoreBoundedDepthSP(getRole(), game.getNextState(getCurrentState(), currAction), 0, 10, 1, finishBy);
					if (currScore > bestSeenScore) {
						bestSeenScore = currScore;
						selectionSP = moves.get(i);
					}
				}
				selectionSP = bestMoveBoundedDepthSP(getRole(), getCurrentState(), 0, 8, 1, finishBy); */
        // Two player game
        else {
            //selectionSP = bestMoveBoundedDepthMP(getRole(), getCurrentState(), 0, 2, 1, finishBy);
            //selection = bestMoveMCS(getRole(), getCurrentState(), finishBy);

            updateTreeMP(finishBy);
            MCTSNodeMP currnode = MachineStateMapMP.get(getCurrentState());
            int index = 0;
            int prev = 0;
            boolean badMove = false;
            double bestscore = 0;
            for (int i = 0; i < currnode.children.size(); i++) {
                if (badMove) {
                    badMove = false;
                    break;
                }
                for (int j = 0; j < currnode.children.get(i).size(); j++) {
                    MCTSNodeMP child = currnode.children.get(i).get(j);
                    if (game.isTerminal(getCurrentState()) && game.getGoal(child.state, getRole()) == 0) {
                        index = prev;
                        badMove = true;
                        break;
                    }
                    if (game.isTerminal(child.state) && game.getGoal(child.state, getRole()) == 100) {
                        selection = moves.get(i);
                        long stop = System.currentTimeMillis();
                        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
                        return selection;
                    }
                    if (child.getRealUtility(ourIndex) > bestscore) {
                        bestscore = child.getRealUtility(ourIndex);
                        if (index != i) prev = index;
                        index = i;
                    }
                }
            }
            if (bestscore == 0) {
                selection = game.getRandomMove(getCurrentState(), getRole());
                System.out.println("Random");
            } else selection = moves.get(index);

   /*         for (int i = 0; i < currnode.children.size(); i++) {
                MCTSNodeMP child = currnode.children.get(i);
                if ((child.utility.get(ourIndex) / child.visits) > bestscore) {
                    bestscore = child.utility.get(ourIndex) / child.visits;
                    index = i;
                }
            }
            if (bestscore == 0) {
                selection = game.getRandomMove(getCurrentState(), getRole());
                System.out.println("Random");
            }
            else {
                MCTSNodeMP bestState = currnode.children.get(index);
                List<List<Move> > jointMoves = game.getLegalJointMoves(getCurrentState());
                for(List<Move> l : jointMoves) {
                    if(game.getNextState(getCurrentState(), l).equals(bestState.state)) {
                        selection = l.get(ourIndex);
                        break;
                    }
                }
            } */
        }

        long stop = System.currentTimeMillis();

        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }

    @Override
    public StateMachine getInitialStateMachine() {
        //return new CachedStateMachine(new ProverStateMachine());
        return new CachedStateMachine(new GladosCompiledPropNetStateMachine());
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

        if(game.getRoles().size() == 1) {
            rootSP = new MCTSNodeSP(null, game.getInitialState());
            MachineStateMapSP = new HashMap<MachineState, MCTSNodeSP>();
            MachineStateMapSP.put(game.getInitialState(), rootSP);
            updateTreeSP(finishBy);
        } else {
            rootMP = new MCTSNodeMP(null, game.getInitialState());
            MachineStateMapMP = new HashMap<MachineState, MCTSNodeMP>();
            MachineStateMapMP.put(game.getInitialState(), rootMP);
            updateTreeMP(finishBy);
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


    /*

    private List<Move> sequentialPlan;
    private Iterator<Move> it;
    private Set<MachineState> seen;
    private int plannedScore;

    */

    /**
     * Defines the algorithm that the player uses to select their move.
     *
     * @param timeout time in milliseconds since the era when this function must return
     * @return Move - the move selected by the player
     * @throws org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException
     * @throws org.ggp.base.util.statemachine.exceptions.MoveDefinitionException
     * @throws org.ggp.base.util.statemachine.exceptions.GoalDefinitionException
     */
    /*@Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // We get the current start time
        long start = System.currentTimeMillis();

        List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());

        // SampleLegalGamer is very simple : it picks the first legal move
        //Move selectionSP = moves.get(0);

        if(plannedScore < 100) {
            seen = new HashSet<MachineState>();
            SequentialPlannerData spd = bestPlan(getRole(), getCurrentState(), timeout);

            if(spd.score > plannedScore) {
                sequentialPlan = spd.plan;
                it = sequentialPlan.iterator();
                plannedScore = spd.score;
            }
        }

        System.out.println("Planned score: " + plannedScore);
        Move selectionSP = it.next();
        it.remove();

        // We get the end time
        // It is mandatory that stop<timeout
        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selectionSP, stop - start));
        return selectionSP;
    }

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        //long start = System.currentTimeMillis();
        seen = new HashSet<MachineState>();
        SequentialPlannerData spd = bestPlan(getRole(), getCurrentState(), timeout);
        sequentialPlan = spd.plan;
        plannedScore = spd.score;
        System.out.println("Planned score: " + plannedScore);
        it = sequentialPlan.iterator();
//        long end = System.currentTimeMillis();
    }*/

    /**
     * Optimal search with timeout for single player games.
     * @param r
     * @param s
     * @return
     * @throws GoalDefinitionException
     * @throws MoveDefinitionException
     */
    /*public SequentialPlannerData bestPlan(Role r, MachineState s, long timeout) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
        //System.out.println(seen.size());
        if(System.currentTimeMillis() > timeout - 1000 || seen.contains(s)) {
            return new SequentialPlannerData(-1, new LinkedList<Move>());
        }
        seen.add(s);
        //System.out.println("Getting best plan at state " + s.toString());
        StateMachine sm = getStateMachine();
        if(sm.isTerminal(s)) {
            return new SequentialPlannerData(sm.getGoal(s, r), new LinkedList<Move>());
        }
        List<Move> moves = sm.getLegalMoves(s, r);
        int score = -1;
        List<Move> bestPlan = new LinkedList<Move>();
        for(Move m : moves) {
            if(score == 100 || System.currentTimeMillis() > timeout - 1000) break; // leave a one second buffer
            List<Move> nextMove = new LinkedList<Move>();
            nextMove.add(m);
            SequentialPlannerData spd = bestPlan(r, sm.getNextState(s, nextMove), timeout);
            if(spd.score > score) {
                score = spd.score;
                bestPlan = spd.plan;
                bestPlan.add(0, m);
            }
        }
        return new SequentialPlannerData(score, bestPlan);
    }

    static class SequentialPlannerData {
        int score;
        List<Move> plan;
        public SequentialPlannerData(int a, List<Move> b) {
            score = a;
            plan = b;
        }
    }*/
    @Override
    public String getName() {
        return "GLaDOS";
    }
}
