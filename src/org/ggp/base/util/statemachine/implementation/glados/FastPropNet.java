package org.ggp.base.util.statemachine.implementation.glados;

public class FastPropNet {
    public boolean[] p; // non-base or input
    public boolean[] b;
    public boolean[] n;
    public FastPropNet() {

    }
    public FastPropNet(int numNonBaseOrInputProps, boolean[] b, boolean[] i) {
        this.b = b;
        n = i;
        p = new boolean[numNonBaseOrInputProps];
    }
    public void setMoves(boolean[] moves) {
        n = moves;
    }
    public void propagate() {

    }
    public void nextState() {

    }
}