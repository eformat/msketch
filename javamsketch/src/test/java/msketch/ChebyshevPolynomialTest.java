package msketch;

import msketch.chebyshev.ChebyshevPolynomial;
import msketch.chebyshev.QuadraticCosFunction;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChebyshevPolynomialTest {
    @Test
    public void testSimple() {
        double[] coeff = {1.0, 2.0, 3.0};
        ChebyshevPolynomial cp = new ChebyshevPolynomial(coeff);
        assertEquals(2.34, cp.value(.7), 1e-10);

        ChebyshevPolynomial cb = ChebyshevPolynomial.basis(2);
        assertEquals(-0.02, cb.value(.7), 1e-10);

        double[] coeff2 = {2.0, 1.0, 3.0};
        cp = new ChebyshevPolynomial(coeff2);
        assertEquals(2.0, cp.integrate(), 1e-10);

        assertEquals(1.848, cp.multiplyByBasis(1).value(.7), 1e-10);
    }

    @Test
    public void testFitMulti() {
        QuadraticCosFunction multiFunction = new QuadraticCosFunction(3);
        ChebyshevPolynomial[] cfit = ChebyshevPolynomial.fitMulti(multiFunction, 1e-10);
        for (int i = 0; i < cfit.length; i++) {
            assertEquals((i+1)*.25, cfit[i].value(.5), 1e-10);
        }
    }

    @Test
    public void testFit() {
        double[] coeff = {1.0, 2.0, 3.0};
        ChebyshevPolynomial cp = new ChebyshevPolynomial(coeff);
        ChebyshevPolynomial cfit = ChebyshevPolynomial.fit(cp, 1e-10);

        for (int i = 0; i < coeff.length; i++) {
            assertEquals(coeff[i], cfit.coeffs()[i], 1e-10);
        }
    }

    @Test
    public void testIntegrate() {
        double[] coeff = {2.0, 1.0, 3.0};
        ChebyshevPolynomial cp = new ChebyshevPolynomial(coeff);
        assertEquals(cp.integrate(), cp.integralPoly().value(1), 1e-10);
    }
}