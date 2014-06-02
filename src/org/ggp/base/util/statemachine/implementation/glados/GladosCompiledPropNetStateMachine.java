package org.ggp.base.util.statemachine.implementation.glados;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.*;
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

public class GladosCompiledPropNetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;

    private Map<Proposition, Integer> orderNumber;
    private Map<Proposition, Integer> baseNumber;
    private Map<Proposition, Integer> inputNumber;

    private List<Proposition> orderedBasePropositions;
    private List<Proposition> orderedInputPropositions;
    private int numBaseProps;
    private int numInputProps;

    private FastPropNet fpn;

    private String className;

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        className = "FastPropNet" + (int)(Math.random() * 100000000);

        try {
            propNet = OptimizingPropNetFactory.create(description);
        } catch(Exception e) {

        }
        roles = propNet.getRoles();
        ordering = getOrdering();

        // dump into index map
        orderNumber = new HashMap<Proposition, Integer>();
        int count = 0;
        for(Proposition p : ordering) {
            orderNumber.put(p, count++);
        }

        // add base and input propositions to list
        orderedBasePropositions = new ArrayList<Proposition>();
        int countBase = 0;
        baseNumber = new HashMap<Proposition, Integer>();
        for(Proposition p : propNet.getBasePropositions().values()) {
            orderedBasePropositions.add(p);
            baseNumber.put(p, countBase++);
        }
        numBaseProps = orderedBasePropositions.size();

        orderedInputPropositions = new ArrayList<Proposition>();
        int countInput = 0;
        inputNumber = new HashMap<Proposition, Integer>();
        for(Proposition p : propNet.getInputPropositions().values()) {
            orderedInputPropositions.add(p);
            inputNumber.put(p, countInput++);
        }
        numInputProps = orderedInputPropositions.size();

        try {
            buildFastPropNetClass(className);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private void buildFastPropNetClass(String className) throws Exception {
        // Modify the existing class FastPropNet
        ClassPool pool = ClassPool.getDefault();
        pool.appendClassPath("bin");

        // build methods
        CtClass cc = pool.makeClass(className);
        cc.setSuperclass(pool.get("org.ggp.base.util.statemachine.implementation.glados.FastPropNet"));
        cc.addMethod(CtNewMethod.make(buildPropagate(), cc));
        cc.addMethod(CtNewMethod.make(buildNextState(), cc));

        // instantiate
        fpn = (FastPropNet)cc.toClass().newInstance();

        // initialize
        fpn.props = new boolean[ordering.size()];
        fpn.inputProps = new boolean[numInputProps];
        fpn.baseProps = new boolean[numBaseProps];
    }

    private String buildPropagate() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("public void propagate(){");
        //sb.append("baseProps = state;\n");
        int pi = 0;
        for(Proposition p : ordering) {
            if(p.getInputs().size() >= 1) {

                sb.append("props[" + pi + "]=");

                Component c = p.getSingleInput();
                sb.append(componentToString(c)); // guaranteed c is not a transition

                sb.append(";");
            }
            pi++;
        }
        sb.append("}");
        //System.out.println(sb);
        //m.setBody(sb.toString());
        return sb.toString();
    }

    private String componentToString(Component c) throws Exception {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        if(c instanceof Proposition) {
            Proposition cp = (Proposition)c;
            if(isBaseProposition(cp)) {
                return "baseProps[" + baseNumber.get(cp) + "]";
            } else if(isInputProposition(cp)) {
                return "inputProps[" + inputNumber.get(cp) + "]";
            } else {
                return "props[" + orderNumber.get(cp) + "]";
            }
        } else if(c instanceof Constant) {
            sb.append(c.getValue());
        } else if(c instanceof And) {
            sb.append("(");
            for(Component ci : c.getInputs()) {
                if(!first) {
                    sb.append("&&");
                }
                sb.append("(");
                sb.append(componentToString(ci));
                sb.append(")");
                first = false;
            }
            sb.append(")");
        } else if(c instanceof Or) {
            sb.append("(");
            for(Component ci : c.getInputs()) {
                if(!first) {
                    sb.append("||");
                }
                sb.append("(");
                sb.append(componentToString(ci));
                sb.append(")");
                first = false;
            }
            sb.append(")");
        } else if(c instanceof Not) {
            Component ci = c.getSingleInput();
            sb.append("!");
            sb.append("(");
            sb.append(componentToString(ci));
            sb.append(")");
        } else if(c instanceof Transition) { // should never get here from "propagate" because we are handling non-base propositions
            Component ci = c.getSingleInput();
            sb.append("(");
            sb.append(componentToString(ci));
            sb.append(")");
        }
        return sb.toString();
    }

    private String buildNextState() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("public void nextState(){");
        int pi = 0;
        for(Proposition p : orderedBasePropositions) {
            sb.append("baseProps[" + pi + "]=");
            Component c = p.getSingleInput();
            sb.append(componentToString(c));
            sb.append(";");
            pi++;
        }
        //sb.append("System.arraycopy(baseProps,0,state,0,state.length);");
        sb.append("}");

        //m.setBody(sb.toString());
        return sb.toString();
    }

    private boolean[] basePropBooleansFromState(MachineState state) {
        Set<GdlSentence> trueState = state.getContents();
        boolean[] ret = new boolean[numBaseProps];
        int c = 0;
        for(Proposition p : orderedBasePropositions) {
            ret[c++] = trueState.contains(p.getName());
        }
        return ret;
    }

    private MachineState stateFromBasePropBooleans(boolean[] b) {
        Set<GdlSentence> contents = new HashSet<GdlSentence>();
        int i = 0;
        for(Proposition p : orderedBasePropositions) {
            if(b[i++]) {
                contents.add(p.getName());
            }
        }
        return new MachineState(contents);
    }

    ///////////// REFACTOR EVERYTHING UNDER HERE

    /**
     * Computes if the state is terminal. Should return the value
     * of the terminal proposition for the state.
     */
    @Override
    public boolean isTerminal(MachineState state) {
        // Compute whether the MachineState is terminal.
        loadPropNet(state);
        return fpn.props[orderNumber.get(propNet.getTerminalProposition())];
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
            if(fpn.props[orderNumber.get(p)]) {
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
        // Compute the initial state.
        fpn.props[orderNumber.get(propNet.getInitProposition())] = true;
        fpn.propagate();
        fpn.nextState();
        /*System.out.println("fpn.baseProps :");
        for(int i = 0; i < fpn.baseProps.length; i++) {
            System.out.print(fpn.baseProps[i] ? 1 : 0);
        }
        System.out.println();
        */
        MachineState initState = stateFromBasePropBooleans(fpn.baseProps);
        fpn.props[orderNumber.get(propNet.getInitProposition())] = false;
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
            if(fpn.props[orderNumber.get(p)]) {
                al.add(getMoveFromProposition(p));
            }
        }
        return al;
    }

    private boolean[] booleansFromMoveList(List<Move> moves) {
        Set<GdlSentence> set = new HashSet<GdlSentence>(toDoes(moves));
        boolean[] moveBooleans = new boolean[numInputProps];
        int pi = 0;
        for(Proposition p : orderedInputPropositions) {
            moveBooleans[pi++] = set.contains(p.getName());
        }
        return moveBooleans;
    }

    /**
     * Computes the next state given state and the list of moves.
     */
    @Override
    public MachineState getNextState(MachineState state, List<Move> moves)
            throws TransitionDefinitionException {

        boolean[] moveBools = booleansFromMoveList(moves);

        // Compute the next state.
        //propNet.renderToFile("before.dot");
        fpn.setMoves(moveBools);

        boolean[] stateBools = basePropBooleansFromState(state);
        fpn.baseProps = stateBools;
        fpn.propagate();
        fpn.nextState();

        //propNet.renderToFile("after.dot");
        return stateFromBasePropBooleans(stateBools);
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

        // Compute the topological ordering.
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
        fpn.baseProps = basePropBooleansFromState(state);
        fpn.propagate();
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