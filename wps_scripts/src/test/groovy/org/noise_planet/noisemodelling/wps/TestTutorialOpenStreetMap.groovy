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
 */

package org.noise_planet.noisemodelling.wps

import org.noise_planet.noisemodelling.wps.Database_Manager.Display_Database
import org.noise_planet.noisemodelling.wps.Import_and_Export.Export_Table
import org.noise_planet.noisemodelling.wps.NoiseModelling.Lden_from_Road_Emission
import org.noise_planet.noisemodelling.wps.Experimental.Road_Emission_From_AADF
import org.noise_planet.noisemodelling.wps.OSM_Tools.Get_Table_from_OSM
import org.noise_planet.noisemodelling.wps.Receivers.Regular_Grid
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Test parsing of zip file using H2GIS database
 */
class TestTutorialOpenStreetMap extends JdbcTestCase {
    Logger LOGGER = LoggerFactory.getLogger(TestTutorialOpenStreetMap.class)

    void testTutorial() {
        // Check empty database
        Object res = new Display_Database().exec(connection, [])
        assertEquals("", res)
        // Import OSM file
        res = new Get_Table_from_OSM().exec(connection,
                ["pathFile": TestTutorialOpenStreetMap.getResource("map.osm.gz").getPath(),
                 "targetSRID" : 2154,
                 "convert2Building" : true,
                 "convert2Vegetation" : true,
                 "convert2Roads" : true])
        // Check database
        res = new Display_Database().exec(connection, [])

        assertTrue(res.contains("SURFACE_OSM"))
        assertTrue(res.contains("BUILDINGS_OSM"))
        assertTrue(res.contains("ROADS"))

        // Check export geojson
        File testPath = new File("target/test.geojson")

        if(testPath.exists()) {
            testPath.delete()
        }

        res = new Export_Table().exec(connection, ["exportPath" : "target/test.geojson",
                                                   "tableToExport": "BUILDINGS_OSM"])
        assertTrue(testPath.exists())

        // Check regular grid

        res = new Regular_Grid().exec(connection, ["delta": 50,
                                                   "sourcesTableName": "ROADS",
                                                   "buildingTableName": "BUILDINGS_OSM"])

        // Check database
        res = new Display_Database().exec(connection, [])

        assertTrue(res.contains("RECEIVERS"))

        new Road_Emission_From_AADF().exec(connection, ["sourcesTableName": "ROADS"])

        // Check database
        res = new Display_Database().exec(connection, [])

        assertTrue(res.contains("LW_ROADS"))

        res = new Lden_from_Road_Emission().exec(connection, ["tableSources"  : "LW_ROADS",
                                                              "tableBuilding" : "BUILDINGS_OSM",
                                                              "tableGroundAbs": "SURFACE_OSM",
                                                              "tableReceivers": "RECEIVERS"])

        // Check database
        res = new Display_Database().exec(connection, [])

        assertTrue(res.contains("LDEN_GEOM"))
    }

}
