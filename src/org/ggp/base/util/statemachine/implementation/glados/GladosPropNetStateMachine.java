package org.ggp.base.util.statemachine.implementation.glados;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;

import java.util.*;

public class GladosPropNetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        try {
            propNet = OptimizingPropNetFactory.create(description);
        } catch(Exception e) {

        }
        roles = propNet.getRoles();
        ordering = getOrdering();

        /*
        for(Proposition p : ordering) {
            System.out.print(p.getName() + " ");
        }
        System.out.println();
        System.out.println("Done with init");
        propNet.renderToFile("propnet_visualization3.dot");
        */
    }

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
        // TODO: Compute whether the MachineState is terminal.
        loadPropNet(state);
        return propNet.getTerminalProposition().getValue();
    }

    /**
     * Computes the goal for a role in the current state.
     * Should return the value of the goal proposition that
     * is true for that role. If there is not exactly one goal
     * proposition true for that role, then you should throw a
     * GoalDefinitionException because the goal is ill-defined.
     */
    @Override
    public int getGoal(MachineState state, Role role)
            throws GoalDefinitionException {
        loadPropNet(state);
        Set<Proposition> goalProps = propNet.getGoalPropositions().get(role);
        for(Proposition p : goalProps) {
            if(p.getValue()) {
                return getGoalValue(p);
            }
        }
        return -1;
    }

    /**
     * Returns the initial state. The initial state can be computed
     * by only setting the truth value of the INIT proposition to true,
     * and then computing the resulting state.
     */
    @Override
    public MachineState getInitialState() {
        // TODO: Compute the initial state.
        propNet.getInitProposition().setValue(true);
        propagateNet();
        MachineState initState = getStateFromBase();
        propNet.getInitProposition().setValue(false);
        return initState;
    }

    /**
     * Computes the legal moves for role in state.
     */
    @Override
    public List<Move> getLegalMoves(MachineState state, Role role)
            throws MoveDefinitionException {
        loadPropNet(state);
        Set<Proposition> legalProps = propNet.getLegalPropositions().get(role);
        ArrayList<Move> al = new ArrayList<Move>();
        for(Proposition p : legalProps) {
            if(p.getValue()) {
                al.add(getMoveFromProposition(p));
            }
        }
        return al;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {
        // TODO: Compute the next state.
        //propNet.renderToFile("before.dot");
        stepPropNet(state, moves);
        //propNet.renderToFile("after.dot");
        return getStateFromBase();
    }

    /**
     * This should compute the topological ordering of propositions.
     * Each component is either a proposition, logical gate, or transition.
     * Logical gates and transitions only have propositions as inputs.
     *
     * The base propositions and input propositions should always be exempt
     * from this ordering.
     *
     * The base propositions values are set from the MachineState that
     * operations are performed on and the input propositions are set from
     * the Moves that operations are performed on as well (if any).
     *
     * @return The order in which the truth values of propositions need to be set.
     */
    public List<Proposition> getOrdering()
    {
        // List to contain the topological ordering.
        List<Proposition> order = new LinkedList<Proposition>();

        // All of the components in the PropNet
        //List<Component> components = new ArrayList<Component>(propNet.getComponents());

        // All of the propositions in the PropNet.
        //List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

        Set<Component> seen = new HashSet<Component>();

        // TODO: Compute the topological ordering.
        for(Component c : propNet.getComponents()) {
            if(!seen.contains(c)) {
                computeTopologicalOrdering(c, order, seen);
            }
        }
        return order;
    }

    private void computeTopologicalOrdering(Component c, List<Proposition> order, Set<Component> seen) {
        //System.out.println(seen.size());
        //System.out.println(c);

        if(c instanceof Transition || isBaseProposition(c) || isInputProposition(c)) {
            seen.add(c);
            return;
        }

        for(Component input : c.getInputs()) {
            if(!seen.contains(input)) {
                computeTopologicalOrdering(input, order, seen);
            }
        }
        if(c instanceof Proposition) {
            order.add((Proposition)c);
        }
        seen.add(c);
    }

    private boolean isBaseProposition(Component c) {
        return c instanceof Proposition && c.getInputs().size() == 1 && c.getSingleInput() instanceof Transition;
    }

    private boolean isInputProposition(Component c) {
        return c instanceof Proposition && ((Proposition) c).getName() instanceof GdlRelation
                && ((Proposition)c).getName().getName().getValue().equals("does");
    }

    private void loadPropNet(MachineState state) {
        Set<GdlSentence> trueSentences = state.getContents();
        for(Proposition baseProp : propNet.getBasePropositions().values()) {
            baseProp.setValue(trueSentences.contains(baseProp.getName()));
        }
        propagateNet();
    }

    private void propagateNet() {
        for(Proposition p : ordering) {
            if(p.getInputs().size() >= 1) { // if the proposition has (one) inputs
                p.setValue(p.getSingleInput().getValue());
            }
        }
    }

    private void stepPropNet(MachineState state, List<Move> moves) {

        // load in previous state into base propositions
        Set<GdlSentence> trueSentences = state.getContents();
        for(Proposition baseProp : propNet.getBasePropositions().values()) {
            baseProp.setValue(trueSentences.contains(baseProp.getName()));
        }

        // load in values of input propositions

        // set all the non-moves and moves to false
        for(Proposition p : propNet.getInputPropositions().values()) {
            p.setValue(false);
        }

        // set all the moves to true
        List<GdlSentence> doeses = toDoes(moves);
        for(GdlSentence sentence : doeses) {
            propNet.getInputPropositions().get(sentence).setValue(true);
        }

        propagateNet();
    }

    /* Already implemented for you */
    @Override
    public List<Role> getRoles() {
        return roles;
    }

	/* Helper methods */

    /**
     * The Input propositions are indexed by (does ?player ?action).
     *
     * This translates a list of Moves (backed by a sentence that is simply ?action)
     * into GdlSentences that can be used to get Propositions from inputPropositions.
     * and accordingly set their values etc.  This is a naive implementation when coupled with
     * setting input values, feel free to change this for a more efficient implementation.
     *
     * @param moves
     * @return
     */
    private List<GdlSentence> toDoes(List<Move> moves)
    {
        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
        Map<Role, Integer> roleIndices = getRoleIndices();

        for (int i = 0; i < roles.size(); i++)
        {
            int index = roleIndices.get(roles.get(i));
            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
        }
        return doeses;
    }

    /**
     * Takes in a Legal Proposition and returns the appropriate corresponding Move
     * @param p
     * @return a PropNetMove
     */
    public static Move getMoveFromProposition(Proposition p)
    {
        return new Move(p.getName().get(1));
    }

    /**
     * Helper method for parsing the value of a goal proposition
     * @param goalProposition
     * @return the integer value of the goal proposition
     */
    private int getGoalValue(Proposition goalProposition)
    {
        GdlRelation relation = (GdlRelation) goalProposition.getName();
        GdlConstant constant = (GdlConstant) relation.get(1);
        return Integer.parseInt(constant.toString());
    }

    /**
     * A Naive implementation that computes a PropNetMachineState
     * from the true BasePropositions.  This is correct but slower than more advanced implementations
     * You need not use this method!
     * @return PropNetMachineState
     */
    public MachineState getStateFromBase()
    {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        for (Proposition p : propNet.getBasePropositions().values())
        {
            p.setValue(p.getSingleInput().getValue());
            //System.out.println(p.getName() + " " + p.getValue());
            if (p.getValue())
            {
                contents.add(p.getName());
            }

        }
        return new MachineState(contents);
    }
}