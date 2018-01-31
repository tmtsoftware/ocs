package edu.gemini.itc.web.html;

import edu.gemini.itc.base.ImagingResult;
import edu.gemini.itc.gems.Gems;
import edu.gemini.itc.iris.Camera;
import edu.gemini.itc.iris.Iris;
import edu.gemini.itc.iris.IrisRecipe;
import edu.gemini.itc.shared.IrisParameters;
import edu.gemini.itc.shared.ItcImagingResult;
import edu.gemini.itc.shared.ItcParameters;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Helper class for printing IRIS calculation results to an output stream.
 */
public final class IrisPrinter extends PrinterBase {

    private final IrisRecipe recipe;

    public IrisPrinter(final ItcParameters p, final IrisParameters instr, final PrintWriter out) {
        super(out);
        recipe = new IrisRecipe(p, instr);
    }

    /**
     * Performes recipe calculation and writes results to a cached PrintWriter or to System.out.
     */
    public void writeOutput() {
        final ImagingResult result = recipe.calculateImaging();
        final ItcImagingResult s = recipe.serviceResult(result);
        writeImagingOutput(result, s);
    }

    private void writeImagingOutput(final ImagingResult result, final ItcImagingResult s) {

        final Iris instrument = (Iris) result.instrument();

        _println("");

        _println((HtmlPrinter.printSummary((Gems) result.aoSystem().get())));

        _print(CalculatablePrinter.getTextResult(result.sfCalc(), false));
        _println(String.format("derived image halo size (FWHM) for a point source = %.2f arcsec.\n", result.iqCalc().getImageQuality()));
        _println(CalculatablePrinter.getTextResult(result.is2nCalc(), result.observation()));
        _println(CalculatablePrinter.getBackgroundLimitResult(result.is2nCalc()));

        _printPeakPixelInfo(s.ccd(0));
        _printWarnings(s.warnings());

        _print("<HR align=left SIZE=3>");

        _println("<b>Input Parameters:</b>");
        _println("Instrument: " + instrument.getName() + "\n");
        _println(HtmlPrinter.printParameterSummary(result.source()));
        _println(irisToString(instrument));
        _println(printTeleParametersSummary(result));
        _println(HtmlPrinter.printParameterSummary((Gems) result.aoSystem().get()));
        _println(HtmlPrinter.printParameterSummary(result.conditions()));
        _println(HtmlPrinter.printParameterSummary(result.observation()));

    }

    private String printTeleParametersSummary(final ImagingResult result) {
        final StringWriter sb = new StringWriter();
        sb.append("Telescope configuration: \n");
        sb.append("<LI>");
        sb.append(result.telescope().getMirrorCoating().displayValue());
        sb.append(" mirror coating.\n");
        sb.append("<LI>wavefront sensor: gems\n");
        return sb.toString();
    }

    private String irisToString(final Iris instrument) {
        String s = "Instrument configuration: \n";
        s += "Optical Components: <BR>";
        for (Object o : instrument.getComponents()) {
            if (!(o instanceof Camera)) {
                s += "<LI>" + o.toString() + "<BR>";
            }
        }
        s += "<BR>";
        s += "Pixel Size: " + instrument.getPixelSize() + "<BR>";

        return s;
    }

}
