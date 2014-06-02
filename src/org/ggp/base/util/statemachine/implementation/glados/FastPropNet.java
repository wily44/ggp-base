package org.ggp.base.util.statemachine.implementation.glados;

public class FastPropNet {
    public boolean[] props; // non-base or input
    public boolean[] baseProps;
    public boolean[] inputProps;
    public FastPropNet() {

    }
    public FastPropNet(int numNonBaseOrInputProps, boolean[] b, boolean[] i) {
        baseProps = b;
        inputProps = i;
        props = new boolean[numNonBaseOrInputProps];
    }
    public void setMoves(boolean[] moves) {
        inputProps = moves;
    }
    public void propagate() {

    }
    public void nextState() {

    }
}