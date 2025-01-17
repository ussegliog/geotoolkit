/*
 *    Geotoolkit.org - An Open Source Java GIS Toolkit
 *    http://www.geotoolkit.org
 *
 *    (C) 2013, Geomatys
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotoolkit.processing.coverage.merge;

import java.awt.image.DataBuffer;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.coverage.grid.GridExtent;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.coverage.grid.GridRoundingMode;
import org.apache.sis.internal.referencing.j2d.AffineTransform2D;
import org.apache.sis.parameter.Parameters;
import org.apache.sis.util.ArgumentChecks;
import org.geotoolkit.coverage.grid.GridGeometry2D;
import org.geotoolkit.process.Process;
import org.geotoolkit.process.ProcessDescriptor;
import org.geotoolkit.process.ProcessException;
import org.geotoolkit.processing.AbstractProcess;
import org.geotoolkit.processing.coverage.bandcombine.BandCombineDescriptor;
import static org.geotoolkit.processing.coverage.merge.MergeDescriptor.*;
import org.geotoolkit.processing.coverage.reformat.ReformatDescriptor;
import org.geotoolkit.processing.coverage.resample.ResampleDescriptor;
import org.opengis.geometry.Envelope;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.datum.PixelInCell;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
public class MergeProcess extends AbstractProcess {

    public MergeProcess(ParameterValueGroup input) {
        super(INSTANCE, input);
    }

    /**
     *
     * @param coverages coverage to merge
     * @param env area to merge
     */
    public MergeProcess(GridCoverage[] coverages, Envelope env){
        super(MergeDescriptor.INSTANCE, asParameters(coverages,env));
    }

    private static ParameterValueGroup asParameters(GridCoverage[] coverages, Envelope env){
        final Parameters params = Parameters.castOrWrap(MergeDescriptor.INPUT_DESC.createValue());
        params.getOrCreate(MergeDescriptor.IN_COVERAGES).setValue(coverages);
        if(env!=null)params.getOrCreate(MergeDescriptor.IN_ENVELOPE).setValue(env);
        return params;
    }

    /**
     * Execute process now.
     *
     * @return merged coverage
     * @throws ProcessException
     */
    public GridCoverage executeNow() throws ProcessException {
        execute();
        return (GridCoverage) outputParameters.parameter(MergeDescriptor.OUT_COVERAGE.getName().getCode()).getValue();
    }

    @Override
    protected void execute() throws ProcessException {
        ArgumentChecks.ensureNonNull("inputParameter", inputParameters);

        // PARAMETERS CHECK ////////////////////////////////////////////////////
        final GridCoverage[] inputCoverage = inputParameters.getValue(IN_COVERAGES);
        final Envelope inputEnvelope = inputParameters.getValue(IN_ENVELOPE);
        final double inputResolution = inputParameters.getValue(IN_RESOLUTION);

        //find the best data type;
        int datatype = -1;
        for(GridCoverage gc : inputCoverage){
            final int gctype = gc.render(null).getSampleModel().getDataType();
            if(datatype==-1){
                datatype = gctype;
            }else{
                //find the largest type
                datatype = largest(datatype, gctype);
            }
        }

        //calculate the output grid geometry and image size
        final AffineTransform2D g2c = new AffineTransform2D(inputResolution, 0, 0, -inputResolution, inputEnvelope.getMinimum(0), inputEnvelope.getMaximum(1));
        final GridGeometry gridGeom = new GridGeometry(PixelInCell.CELL_CORNER, g2c, inputEnvelope, GridRoundingMode.ENCLOSING);

        //force sample type and area of each coverage
        final GridCoverage[] fittedCoverages = new GridCoverage[inputCoverage.length];
        for(int i=0;i<inputCoverage.length;i++){
            fittedCoverages[i] = inputCoverage[i];

            //Reformat
            final ProcessDescriptor coverageReformatDesc = ReformatDescriptor.INSTANCE;
            final ParameterValueGroup reformatParams = coverageReformatDesc.getInputDescriptor().createValue();
            reformatParams.parameter("coverage").setValue(fittedCoverages[i]);
            reformatParams.parameter("datatype").setValue(datatype);
            final Process reformatProcess = coverageReformatDesc.createProcess(reformatParams);
            fittedCoverages[i] = (GridCoverage) reformatProcess.call().parameter("result").getValue();

            //Resample
            final ProcessDescriptor coverageResampleDesc = ResampleDescriptor.INSTANCE;
            final ParameterValueGroup resampleParams = coverageResampleDesc.getInputDescriptor().createValue();
            resampleParams.parameter("Source").setValue(fittedCoverages[i]);
            resampleParams.parameter("GridGeometry").setValue(gridGeom);
            resampleParams.parameter("CoordinateReferenceSystem").setValue(inputEnvelope.getCoordinateReferenceSystem());
            final Process resampleProcess = coverageResampleDesc.createProcess(resampleParams);
            fittedCoverages[i] = (GridCoverage) resampleProcess.call().parameter("result").getValue();
        }

        //Band combine
        final ProcessDescriptor coverageResampleDesc = BandCombineDescriptor.INSTANCE;
        final ParameterValueGroup resampleParams = coverageResampleDesc.getInputDescriptor().createValue();
        resampleParams.parameter("coverages").setValue(fittedCoverages);
        final Process resampleProcess = coverageResampleDesc.createProcess(resampleParams);
        final GridCoverage result = (GridCoverage) resampleProcess.call().parameter("result").getValue();

        outputParameters.getOrCreate(OUT_COVERAGE).setValue(result);
    }

    private static int largest(int datatype1, int datatype2){
        if(datatype1 == DataBuffer.TYPE_DOUBLE || datatype2 == DataBuffer.TYPE_DOUBLE){
            return DataBuffer.TYPE_DOUBLE;
        }else if(datatype1 == DataBuffer.TYPE_FLOAT || datatype2 == DataBuffer.TYPE_FLOAT){
            return DataBuffer.TYPE_FLOAT;
        }else if(datatype1 == DataBuffer.TYPE_INT || datatype2 == DataBuffer.TYPE_INT){
            return DataBuffer.TYPE_INT;
        }else if(datatype1 == DataBuffer.TYPE_USHORT || datatype2 == DataBuffer.TYPE_USHORT){
            return DataBuffer.TYPE_USHORT;
        }else if(datatype1 == DataBuffer.TYPE_SHORT || datatype2 == DataBuffer.TYPE_SHORT){
            return DataBuffer.TYPE_USHORT;
        }else if(datatype1 == DataBuffer.TYPE_BYTE || datatype2 == DataBuffer.TYPE_BYTE){
            return DataBuffer.TYPE_BYTE;
        }
        return DataBuffer.TYPE_UNDEFINED;
    }

}
