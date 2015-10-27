///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

/**
 * Generates (nonrecursively) all of the selections from a items, where a is
 * a nonnegative integer.  The values of a is given in the
 * constructor, and the sequence of seletions is obtained by repeatedly calling
 * the next() method.  When the sequence is finished, null is returned.</p> </p>
 * <p>A valid combination for the sequence of combinations for a
 * generated by this class is an array x[] of a integers i, 0 <= i < a.
 * <p/>
 * To see what this class does, try calling SelectionGenerator.testPrint(5, 3), for
 * instance.
 *
 * @author Joseph Ramsey
 */
@SuppressWarnings({"WeakerAccess"})
public final class SelectionGenerator {

    /**
     * The number of objects being selected from.
     */
    private int a;

    /**
     * The internally stored choice.
     */
    private int[] selectionLocal;

    /**
     * The selection that is returned. Used, since the returned array can be
     * modified by the user.
     */
    private int[] selectionReturned;

    /**
     * Indicates whether the next() method has been called since the last
     * initialization.
     */
    private boolean begun;

    /**
     * Constructs a new selection generator for a items. Once this
     * initialization has been performed, successive calls to next() will
     * produce the series of selections.
     *
     * @param a the number of objects being selected from.
     */
    public SelectionGenerator(int a) {
        if ((a < 0)) {
            throw new IllegalArgumentException(
                    "a must be non-zero");
        }

        this.a = a;
        selectionLocal = new int[a];
        selectionReturned = new int[a];


        // Initialize the choice array with successive integers [0 1 2 ...].
        // Set the value at the last index one less than it would be in such
        // a series, ([0 1 2 ... b - 2]) so that on the first call to next()
        // the first combination ([0 1 2 ... b - 1]) is returned correctly.
        for (int i = 1; i < a; i++) {
            selectionLocal[i] = 0;
        }

        if (a > 0) {
            selectionLocal[a - 1] = -1;
        }

        begun = false;
    }

    /**
     * Returns the next selection in the series, or null if the series is
     * finished.  The array that is produced should not be altered by the user,
     * as it is reused by the selection generator.
     * <p/>
     * If the number of items chosen is zero, a zero-length array will be
     * returned once, with null after that.
     * <p/>
     * The array that is returned is reused, but modifying it will not change
     * the sequence of choices returned.
     *
     * @return the next selection in the series, or null if the series is
     *         finished.
     */
    public int[] next() {
        int i = getA();

        // Scan from the right for the first index whose value is less than
        // its expected maximum (i + diff) and perform the fill() operation
        // at that index.
        while (--i > -1) {
            if (this.selectionLocal[i] < getA() - 1) {
                this.selectionLocal[i]++;

                for (int i1 = i + 1; i1 < getA(); i1++) {
                    this.selectionLocal[i1] = 0;
                }

                begun = true;
                System.arraycopy(selectionLocal, 0, selectionReturned, 0, a);
                return selectionReturned;
            }
        }

        if (this.begun) {
            return null;
        } else {
            begun = true;
            System.arraycopy(selectionLocal, 0, selectionReturned, 0, a);
            return selectionReturned;
        }
    }

    /**
     * This static method will print the series of combinations for a choose b
     * to System.out.
     *
     * @param a the number of objects being selected from.
     */
    public static void testPrint(int a) {
        SelectionGenerator cg = new SelectionGenerator(a);
        int[] selection;

        System.out.println();
        System.out.println(
                "Printing selections for " + a + " items:");
        System.out.println();

        while ((selection = cg.next()) != null) {
            if (selection.length == 0) {
                System.out.println("zero-length array");
            } else {
                for (int aSelection : selection) {
                    System.out.print(aSelection + "\t");
                }

                System.out.println();
            }
        }

        System.out.println();
    }

    /**
     * Returns the number of objects being chosen from.
     *
     * @return Ibid.
     */
    public int getA() {
        return this.a;
    }

}





