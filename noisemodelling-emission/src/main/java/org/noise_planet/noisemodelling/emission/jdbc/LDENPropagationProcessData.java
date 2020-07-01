/**
 * NoiseModelling is a free and open-source tool designed to produce environmental noise maps on very large urban areas. It can be used as a Java library or be controlled through a user friendly web interface.
 *
 * This version is developed by Université Gustave Eiffel and CNRS
 * <http://noise-planet.org/noisemodelling.html>
 * as part of:
 * the Eval-PDU project (ANR-08-VILL-0005) 2008-2011, funded by the Agence Nationale de la Recherche (French)
 * the CENSE project (ANR-16-CE22-0012) 2017-2021, funded by the Agence Nationale de la Recherche (French)
 * the Nature4cities (N4C) project, funded by European Union’s Horizon 2020 research and innovation programme under grant agreement No 730468
 *
 * Noisemap is distributed under GPL 3 license.
 *
 * Contact: contact@noise-planet.org
 *
 * Copyright (C) 2011-2012 IRSTV (FR CNRS 2488) and Ifsttar
 * Copyright (C) 2013-2019 Ifsttar and CNRS
 * Copyright (C) 2020 Université Gustave Eiffel and CNRS
 *
 * @Author Pierre Aumond, Université Gustave Eiffel
 * @Author Nicolas Fortin, Université Gustave Eiffel
 */

package org.noise_planet.noisemodelling.emission.jdbc;

        import org.h2gis.utilities.JDBCUtilities;
        import org.h2gis.utilities.SpatialResultSet;
        import org.locationtech.jts.geom.Coordinate;
        import org.locationtech.jts.geom.Geometry;
        import org.noise_planet.noisemodelling.emission.EvaluateRoadSourceCnossos;
        import org.noise_planet.noisemodelling.emission.EvaluateTrainSourceNMPB;
        import org.noise_planet.noisemodelling.emission.RSParametersCnossos;
        import org.noise_planet.noisemodelling.emission.TrainParametersNMPB;
        import org.noise_planet.noisemodelling.propagation.ComputeRays;
        import org.noise_planet.noisemodelling.propagation.FastObstructionTest;
        import org.noise_planet.noisemodelling.propagation.PropagationProcessData;
        import org.noise_planet.noisemodelling.propagation.PropagationProcessPathData;
        import org.orbisgis.noisemap.core.RSParameters;

        import java.sql.ResultSet;
        import java.sql.SQLException;
        import java.util.*;

/**
 * Read source database and compute the sound emission spectrum of roads sources
 */
public class LDENPropagationProcessData extends PropagationProcessData {
    String lwFrequencyPrepend = "LW";
    public Map<String, Integer> sourceFields = null;

    // Source value in energetic  e = pow(10, dbVal / 10.0)
    public List<double[]> wjSourcesD = new ArrayList<>();
    public List<double[]> wjSourcesE = new ArrayList<>();
    public List<double[]> wjSourcesN = new ArrayList<>();
    public List<double[]> wjSourcesDEN = new ArrayList<>();

    public Map<Long, Integer> SourcesPk = new HashMap<>();

    int idSource = 0;

    LDENConfig ldenConfig;

    public LDENPropagationProcessData(FastObstructionTest freeFieldFinder, LDENConfig ldenConfig) {
        super(freeFieldFinder);
        this.ldenConfig = ldenConfig;
    }

    @Override
    public void addSource(Long pk, Geometry geom, SpatialResultSet rs) throws SQLException {
        super.addSource(pk, geom, rs);
        SourcesPk.put(pk, idSource++);
        double[][] res = computeLw(rs);
        if(ldenConfig.computeLDay) {
            wjSourcesD.add(res[0]);
        }
        if(ldenConfig.computeLEvening) {
            wjSourcesE.add(res[1]);
        }
        if(ldenConfig.computeLNight) {
            wjSourcesN.add(res[2]);
        }
        if(ldenConfig.computeLDEN) {
            wjSourcesDEN.add(res[3]);
        }
    }

    /**
     * @param rs result set of source
     * @param period D or E or N
     * @param slope Gradient percentage of road from -12 % to 12 %
     * @return Emission spectrum in dB
     */
    public double[] getEmissionFromResultSet(ResultSet rs, String period, double slope) throws SQLException {
        if (sourceFields == null) {
            sourceFields = new HashMap<>();
            int fieldId = 1;
            for (String fieldName : JDBCUtilities.getFieldNames(rs.getMetaData())) {
                sourceFields.put(fieldName.toUpperCase(), fieldId++);
            }
        }
        double[] lvl = new double[PropagationProcessPathData.freq_lvl.size()];
        // Set default values
        double tv = 0; // old format "total vehicles"
        double hv = 0; // old format "heavy vehicles"
        double lv_speed = 0;
        double mv_speed = 0;
        double hgv_speed = 0;
        double wav_speed = 0;
        double wbv_speed = 0;
        double lvPerHour = 0;
        double mvPerHour = 0;
        double hgvPerHour = 0;
        double wavPerHour = 0;
        double wbvPerHour = 0;
        double temperature = 20.0;
        String roadSurface = "NL08";
        double tsStud = 0;
        double pmStud = 0;
        double junctionDistance = 100; // no acceleration of deceleration changes with dist >= 100
        int junctionType = 2;

        // Read fields
        if(sourceFields.containsKey("LV_SPD_"+period)) {
            lv_speed = rs.getDouble(sourceFields.get("LV_SPD_"+period));
        }
        if(sourceFields.containsKey("MV_SPD_"+period)) {
            mv_speed = rs.getDouble(sourceFields.get("MV_SPD_"+period));
        }
        if(sourceFields.containsKey("HGV_SPD_"+period)) {
            hgv_speed = rs.getDouble(sourceFields.get("HGV_SPD_"+period));
        }
        if(sourceFields.containsKey("WAV_SPD_"+period)) {
            wav_speed = rs.getDouble(sourceFields.get("WAV_SPD_"+period));
        }
        if(sourceFields.containsKey("WBV_SPD_"+period)) {
            wbv_speed = rs.getDouble(sourceFields.get("WBV_SPD_"+period));
        }
        if(sourceFields.containsKey("LV_"+period)) {
            lvPerHour = rs.getDouble(sourceFields.get("LV_"+period));
        }
        if(sourceFields.containsKey("MV_"+period)) {
            mvPerHour = rs.getDouble(sourceFields.get("MV_"+period));
        }
        if(sourceFields.containsKey("HGV_"+period)) {
            hgvPerHour = rs.getDouble(sourceFields.get("HGV_"+period));
        }
        if(sourceFields.containsKey("WAV_"+period)) {
            wavPerHour = rs.getDouble(sourceFields.get("WAV_"+period));
        }
        if(sourceFields.containsKey("WBV_"+period)) {
            wbvPerHour = rs.getDouble(sourceFields.get("WBV_"+period));
        }
        if(sourceFields.containsKey("PVMT")) {
            roadSurface= rs.getString(sourceFields.get("PVMT"));
        }
        if(sourceFields.containsKey("TEMP_"+period)) {
            temperature = rs.getDouble(sourceFields.get("TEMP_"+period));
        }
        if(sourceFields.containsKey("TS_STUD")) {
            tsStud = rs.getDouble(sourceFields.get("TS_STUD"));
        }
        if(sourceFields.containsKey("PM_STUD")) {
            pmStud = rs.getDouble(sourceFields.get("PM_STUD"));
        }
        if(sourceFields.containsKey("JUNC_DIST")) {
            junctionDistance = rs.getDouble(sourceFields.get("JUNC_DIST"));
        }
        if(sourceFields.containsKey("JUNC_TYPE")) {
            junctionType = rs.getInt(sourceFields.get("JUNC_TYPE"));
        }

        // old fields
        if(sourceFields.containsKey("TV_"+period)) {
            tv = rs.getDouble(sourceFields.get("TV_"+period));
        }
        if(sourceFields.containsKey("HV_"+period)) {
            hv = rs.getDouble(sourceFields.get("HV_"+period));
        }
        if(sourceFields.containsKey("HV_SPD_"+period)) {
            hgv_speed = rs.getDouble(sourceFields.get("HV_SPD_"+period));
        }

        if(tv > 0) {
            lvPerHour = tv - (hv + mvPerHour + hgvPerHour + wavPerHour + wbvPerHour);
        }
        if(hv > 0) {
            hgvPerHour = hv;
        }
        // Compute emission
        int idFreq = 0;
        for (int freq : PropagationProcessPathData.freq_lvl) {
            RSParametersCnossos rsParametersCnossos = new RSParametersCnossos(lv_speed, mv_speed, hgv_speed, wav_speed,
                    wbv_speed,lvPerHour, mvPerHour, hgvPerHour, wavPerHour, wbvPerHour, freq, temperature,
                    roadSurface, tsStud, pmStud, junctionDistance, junctionType);
            rsParametersCnossos.setSlopePercentage(slope);
            rsParametersCnossos.setCoeffVer(ldenConfig.coefficientVersion);
            lvl[idFreq++] = EvaluateRoadSourceCnossos.evaluate(rsParametersCnossos);
        }
        return lvl;
    }

    public double[][] computeLw(SpatialResultSet rs) throws SQLException {

        // Compute day average level
        double[] ld = new double[PropagationProcessPathData.freq_lvl.size()];
        double[] le = new double[PropagationProcessPathData.freq_lvl.size()];
        double[] ln = new double[PropagationProcessPathData.freq_lvl.size()];
        double[] lden = new double[PropagationProcessPathData.freq_lvl.size()];

        if (ldenConfig.input_mode == LDENConfig.INPUT_MODE.INPUT_MODE_PROBA) {
            double val = ComputeRays.dbaToW(90.0);
            for(int idfreq = 0; idfreq < PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                ld[idfreq] = ComputeRays.dbaToW(val);
                le[idfreq] = ComputeRays.dbaToW(val);
                ln[idfreq] = ComputeRays.dbaToW(val);
            }
        } else if (ldenConfig.input_mode == LDENConfig.INPUT_MODE.INPUT_MODE_LW_DEN) {
            // Read average 24h traffic
            for (int idfreq = 0; idfreq < PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                ld[idfreq] = ComputeRays.dbaToW(rs.getDouble(lwFrequencyPrepend + "D" +
                        PropagationProcessPathData.freq_lvl.get(idfreq)));
            }
            for (int idfreq = 0; idfreq < PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                le[idfreq] = ComputeRays.dbaToW(rs.getDouble(lwFrequencyPrepend + "E" +
                        PropagationProcessPathData.freq_lvl.get(idfreq)));
            }
            for (int idfreq = 0; idfreq < PropagationProcessPathData.freq_lvl.size(); idfreq++) {
                ln[idfreq] = ComputeRays.dbaToW(rs.getDouble(lwFrequencyPrepend + "N" +
                        PropagationProcessPathData.freq_lvl.get(idfreq)));
            }
        } else if(ldenConfig.input_mode == LDENConfig.INPUT_MODE.INPUT_MODE_TRAFFIC_FLOW) {
            // Extract road slope
            double slope = 0;
            try {
                Geometry g = rs.getGeometry();
                if(freeFieldFinder!=null && g != null && !g.isEmpty()) {
                    Coordinate[] c = g.getCoordinates();
                    if(c.length >= 2) {
                        double z0 = freeFieldFinder.getHeightAtPosition(c[0]);
                        double z1 = freeFieldFinder.getHeightAtPosition(c[1]);
                        if(!Double.isNaN(z0) && !Double.isNaN(z1)) {
                            slope = RSParameters.computeSlope(z0, z1, g.getLength());
                        }
                    }
                }
            } catch (SQLException ex) {
                // ignore
            }
            // Day
            ld = ComputeRays.dbaToW(getEmissionFromResultSet(rs, "D", slope));

            // Evening
            le = ComputeRays.dbaToW(getEmissionFromResultSet(rs, "E", slope));

            // Night
            ln = ComputeRays.dbaToW(getEmissionFromResultSet(rs, "N", slope));

        }else if(ldenConfig.input_mode == LDENConfig.INPUT_MODE.INPUT_MODE_RAIL_FLOW) {
            // Extract road slope
            double slope = 0;
            String format = "GEOSTANDARD";

            // Day
            ld = getRailEmissionFromResultSet(rs, "D", format);

            // Evening
            le = getRailEmissionFromResultSet(rs, "E", format);

            // Night
            ln = getRailEmissionFromResultSet(rs, "N", format);
        }

        // Combine day evening night sound levels
        for (int idfreq = 0; idfreq < PropagationProcessPathData.freq_lvl.size(); idfreq++) {
            lden[idfreq] = (12 * ld[idfreq] + 4 * ComputeRays.dbaToW(ComputeRays.wToDba(le[idfreq]) + 5) + 8 * ComputeRays.dbaToW(ComputeRays.wToDba(ln[idfreq]) + 10)) / 24.0;
        }

        return new double[][] {ld, le, ln, lden};
    }

    public double[] getRailEmissionFromResultSet(ResultSet rs, String period,String format) throws SQLException {// TODO ADRIEN
        if (sourceFields == null) {
            sourceFields = new HashMap<>();
            int fieldId = 1;
            for (String fieldName : JDBCUtilities.getFieldNames(rs.getMetaData())) {
                sourceFields.put(fieldName.toUpperCase(), fieldId++);
            }
        }
        double[] lvl = new double[PropagationProcessPathData.third_freq_lvl.size()];

        double[] wWagon = new double[PropagationProcessPathData.third_freq_lvl.size()];
        double[] wtrain = new double[PropagationProcessPathData.third_freq_lvl.size()];

        // Set default values
        int numVeh = 1;
        double speed = 0;
        double vehPerHour = 1;
        String typeEngMoteur = "BB22200"; // TODO set default value WPS ?
        String typeWagon = "Corail-FF"; // TODO set default value WPS ?
        double nbVoitWag = 0;

        switch (period) {
            case "D":
                if (sourceFields.containsKey("TDIURNE")) {
                    vehPerHour = rs.getDouble(sourceFields.get("TDIURNE"));
                }
                break;
            case "E":
                if (sourceFields.containsKey("TSOIR")) {
                    vehPerHour = rs.getDouble(sourceFields.get("TSOIR"));
                }
                break;
            case "N":
                if (sourceFields.containsKey("TNUIT")) {
                    vehPerHour = rs.getDouble(sourceFields.get("TNUIT"));
                }
                break;
        }
        switch (format) {
            case "GEOSTANDARD":
                if (sourceFields.containsKey("ENGMOTEUR")) {
                    typeEngMoteur = rs.getString(sourceFields.get("ENGMOTEUR"));
                }
                if (sourceFields.containsKey("TYPVOITWAG")) {
                    typeWagon = rs.getString(sourceFields.get("TYPVOITWAG"));
                }
                if (sourceFields.containsKey("NBVOITWAG")) {
                    nbVoitWag = rs.getDouble(sourceFields.get("NBVOITWAG"));
                }
                if (sourceFields.containsKey("VMAXINFRA")) {
                    speed = rs.getDouble(sourceFields.get("VMAXINFRA"));
                }
                break;
            case "SHORT":
                // Read fields
                if (sourceFields.containsKey("Q")) {
                    vehPerHour = rs.getDouble(sourceFields.get("Q"));
                }
                if (sourceFields.containsKey("SPEED")) {
                    speed = rs.getDouble(sourceFields.get("SPEED"));
                    //speed = min(Vamx et Vtroncon)
                }
                if (sourceFields.containsKey("NAME")) {
                    typeEngMoteur = rs.getString(sourceFields.get("NAME"));
                }
                break;

        }
        // Compute emission
        if (vehPerHour != 0) {
            int idFreq = 0;
            for (int freq : PropagationProcessPathData.third_freq_lvl) {
                if (nbVoitWag != 0) {
                    speed = EvaluateTrainSourceNMPB.evaluateSpeed(typeEngMoteur,typeWagon,speed);
                    TrainParametersNMPB typVoitWagMoteurParametersNMPB = new TrainParametersNMPB(typeWagon, speed, nbVoitWag, numVeh, ldenConfig.getTrainHeight(), freq);
                    wWagon[idFreq]=ComputeRays.dbaToW(EvaluateTrainSourceNMPB.evaluate(typVoitWagMoteurParametersNMPB));
                }
                TrainParametersNMPB engMoteurParametersNMPB = new TrainParametersNMPB(typeEngMoteur, speed, vehPerHour, numVeh, ldenConfig.getTrainHeight(), freq); // TODO a simplifier

                if(1!=2){
                    wtrain[idFreq] = ComputeRays.dbaToW(EvaluateTrainSourceNMPB.evaluate(engMoteurParametersNMPB))+wWagon[idFreq];
                }else{
                    wtrain[idFreq]=0;
                }
                idFreq++;
            }
            lvl = ComputeRays.wToDba(wtrain);
        }
        return lvl;
    }

    public double[] getMaximalSourcePower(int sourceId) {
        if(ldenConfig.computeLDEN && sourceId < wjSourcesDEN.size()) {
            return wjSourcesDEN.get(sourceId);
        } else if(ldenConfig.computeLDay && sourceId < wjSourcesD.size()) {
            return wjSourcesD.get(sourceId);
        } else if(ldenConfig.computeLEvening && sourceId < wjSourcesE.size()) {
            return wjSourcesE.get(sourceId);
        } else if(ldenConfig.computeLNight && sourceId < wjSourcesN.size()) {
            return wjSourcesN.get(sourceId);
        } else {
            return new double[0];
        }
    }
}