--
-- PostgreSQL database dump
--

\restrict 8LYIZJftZsVgf43hwxl4ehdoGrHwWO1uDRUvf7EtSgu7XhSrWsoypwpGLJXWUpM

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg13+2)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: railway_state_snapshot; Type: TABLE; Schema: public; Owner: ferrovia
--

CREATE TABLE public.railway_state_snapshot (
    id integer NOT NULL,
    payload jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT railway_state_snapshot_id_check CHECK ((id = 1))
);


ALTER TABLE public.railway_state_snapshot OWNER TO ferrovia;

--
-- Name: storico_transiti; Type: TABLE; Schema: public; Owner: ferrovia
--

CREATE TABLE public.storico_transiti (
    id_transito text NOT NULL,
    id_treno text NOT NULL,
    id_stazione text NOT NULL,
    movimento text,
    istante timestamp with time zone NOT NULL
);


ALTER TABLE public.storico_transiti OWNER TO ferrovia;

--
-- Data for Name: railway_state_snapshot; Type: TABLE DATA; Schema: public; Owner: ferrovia
--

COPY public.railway_state_snapshot (id, payload, updated_at) FROM stdin;
1	{"treni": [{"id": "T001", "stato": "SOPPRESSO", "idPercorso": "P001", "indiceTappa": 0, "arrivoStimato": null, "ritardoSecondi": 0, "prossimaStazione": "S02", "arrivoProgrammato": null, "stazionePrecedente": "S01", "istanteArrivoStazione": null, "ultimoArrivoEffettivo": null}, {"id": "T002", "stato": "IN_STAZIONE", "idPercorso": "P001", "indiceTappa": 2, "arrivoStimato": null, "ritardoSecondi": 0, "prossimaStazione": null, "arrivoProgrammato": null, "stazionePrecedente": "S01", "istanteArrivoStazione": 1795190266.800225400, "ultimoArrivoEffettivo": 1795190266.792799000}, {"id": "T003", "stato": "IN_STAZIONE", "idPercorso": "P001", "indiceTappa": 2, "arrivoStimato": null, "ritardoSecondi": 0, "prossimaStazione": null, "arrivoProgrammato": null, "stazionePrecedente": "S01", "istanteArrivoStazione": 1795190294.080256700, "ultimoArrivoEffettivo": 1795190294.073329200}], "guasti": [{"id": "GS-ca233ba4-25eb-46b1-808e-4a2271654645", "stato": "RISOLTO", "idSensore": null, "timestamp": 1795021501.301682300, "idStazione": "S02", "descrizione": "Guasto non temporaneo alla stazione S02"}, {"id": "G002", "stato": "RISOLTO", "idSensore": "SENS03", "timestamp": 1795024786.612456700, "idStazione": "S02", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "GS-8c7470a4-f51e-4874-aafd-7a183ed55f70", "stato": "RISOLTO", "idSensore": null, "timestamp": 1795025331.234187700, "idStazione": "S02", "descrizione": "Guasto non temporaneo alla stazione S02"}, {"id": "G004", "stato": "RISOLTO", "idSensore": "SENS04", "timestamp": 1795025860.719108100, "idStazione": "S03", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G005", "stato": "RISOLTO", "idSensore": "SENS03", "timestamp": 1795026421.930805900, "idStazione": "S02", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G006", "stato": "RISOLTO", "idSensore": "SENS02", "timestamp": 1795026422.317347100, "idStazione": "S01", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G007", "stato": "RISOLTO", "idSensore": "SENS03", "timestamp": 1795028033.130890500, "idStazione": "S02", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G008", "stato": "RISOLTO", "idSensore": "SENS02", "timestamp": 1795028664.279916000, "idStazione": "S01", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G009", "stato": "RISOLTO", "idSensore": "SENS01", "timestamp": 1795029104.282890700, "idStazione": "S01", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "GS-39dc3927-7202-4ede-b351-a1fb2a57cc92", "stato": "RISOLTO", "idSensore": null, "timestamp": 1795029274.625487200, "idStazione": "S04", "descrizione": "Guasto non temporaneo alla stazione S04"}, {"id": "G011", "stato": "RISOLTO", "idSensore": "SENS02", "timestamp": 1795029712.291651900, "idStazione": "S01", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "GS-3b37ed02-fedc-4ca2-977d-95b9b3816f2a", "stato": "RISOLTO", "idSensore": null, "timestamp": 1795029717.504746300, "idStazione": "S04", "descrizione": "Guasto non temporaneo alla stazione S04"}, {"id": "GS-4f4e8797-486e-4cb9-99db-8aec201a1725", "stato": "RISOLTO", "idSensore": null, "timestamp": 1795029717.612345600, "idStazione": "S04", "descrizione": "Guasto non temporaneo alla stazione S04"}, {"id": "G014", "stato": "RISOLTO", "idSensore": "SENS06", "timestamp": 1795088898.458227100, "idStazione": "S04", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G015", "stato": "RISOLTO", "idSensore": "SENS01", "timestamp": 1795088898.553991800, "idStazione": "S01", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G016", "stato": "RISOLTO", "idSensore": "SENS02", "timestamp": 1795088898.689630500, "idStazione": "S01", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G017", "stato": "RISOLTO", "idSensore": "SENS04", "timestamp": 1795093124.493078600, "idStazione": "S03", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G018", "stato": "RISOLTO", "idSensore": "SENS05", "timestamp": 1795093252.605817200, "idStazione": "S04", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G019", "stato": "RISOLTO", "idSensore": "SENS03", "timestamp": 1795093253.025456200, "idStazione": "S02", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}, {"id": "G020", "stato": "APERTO", "idSensore": "SENS02", "timestamp": 1795192304.320267400, "idStazione": "S01", "descrizione": "Anomalia rilevata automaticamente dal monitoraggio"}], "percorsi": [{"id": "P001", "nome": "Linea Centrale-Nord", "stazioni": ["S01", "S02"]}], "stazioni": [{"id": "S01", "nome": "Stazione Centrale", "sensori": [{"id": "SENS01", "attivo": true, "inAnomalia": false}, {"id": "SENS02", "attivo": true, "inAnomalia": true}], "operativa": true}, {"id": "S02", "nome": "Stazione Nord", "sensori": [{"id": "SENS03", "attivo": true, "inAnomalia": false}], "operativa": true}, {"id": "S03", "nome": "Stazione Sud", "sensori": [{"id": "SENS04", "attivo": true, "inAnomalia": false}], "operativa": true}, {"id": "S04", "nome": "Stazione Est", "sensori": [{"id": "SENS05", "attivo": true, "inAnomalia": false}, {"id": "SENS06", "attivo": true, "inAnomalia": false}], "operativa": true}], "transiti": [{"idTreno": "T003", "timestamp": 1794999916.164706800, "idStazione": "S01", "idRichiesta": "cdfb2292-a179-4b64-bc3a-4037437161bc", "tipoMovimento": "USCITA"}, {"idTreno": "T001", "timestamp": 1794999916.259118800, "idStazione": "S01", "idRichiesta": "f16ff5d4-1f45-4ca3-a3c6-9c25e8367d16", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1794999916.274732400, "idStazione": "S01", "idRichiesta": "b5476fb2-80a3-4a82-bb95-32851509929c", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1794999924.507721200, "idStazione": "S02", "idRichiesta": "a47e6adb-7cc3-4c4d-bae8-6c3215b7918d", "tipoMovimento": "INGRESSO"}, {"idTreno": "T001", "timestamp": 1794999924.600264100, "idStazione": "S02", "idRichiesta": "001f6622-b431-4bf0-bf3d-c11ae62651d7", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1794999924.616691200, "idStazione": "S02", "idRichiesta": "2da94d20-e559-4fb0-b323-6aa711ea86f6", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1794999924.684948200, "idStazione": "S02", "idRichiesta": "72f12338-7f69-4ee2-b3c3-a56fbaf038bc", "tipoMovimento": "USCITA"}, {"idTreno": "T001", "timestamp": 1794999924.776181700, "idStazione": "S02", "idRichiesta": "09158fc1-621f-4c5c-b650-6e10c2e76cc2", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1794999924.867409300, "idStazione": "S02", "idRichiesta": "ba38874b-a3a3-42e0-8321-fae287dbcb54", "tipoMovimento": "USCITA"}, {"idTreno": "T001", "timestamp": 1794999927.002112600, "idStazione": "S01", "idRichiesta": "1a1cd011-48f4-4610-8575-3a592474ac58", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1794999927.050721300, "idStazione": "S01", "idRichiesta": "a541d859-03e0-4e29-9f4a-76b81e025afc", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1794999928.477289900, "idStazione": "S01", "idRichiesta": "d78785b4-278c-4a3c-a82d-564346b1a6a9", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795015192.542974400, "idStazione": "S01", "idRichiesta": "8230079a-24b9-471b-a588-bc5fa45ed26e", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795015192.580204800, "idStazione": "S01", "idRichiesta": "36ac7986-6e16-4b1d-9057-9b363d73152c", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795015194.656633500, "idStazione": "S02", "idRichiesta": "b0eb2e41-1f2d-4720-820f-43cde5ded9f4", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795015194.690057400, "idStazione": "S02", "idRichiesta": "e6cf6068-f151-40a9-99eb-211f1b6338e7", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795015194.731322400, "idStazione": "S02", "idRichiesta": "f39f3f1f-5097-4208-9674-e00e7510e8a4", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795015194.812743400, "idStazione": "S02", "idRichiesta": "98c05aab-01c7-417b-b0f7-0b9cb3dea80f", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795015196.856113500, "idStazione": "S01", "idRichiesta": "a2e35f2a-2364-4a8e-a053-6bddfee866a6", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795015198.328850200, "idStazione": "S01", "idRichiesta": "43c3c256-98cd-4b4a-b8ba-7d9b145167e5", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795017482.742422800, "idStazione": "S01", "idRichiesta": "3359019b-927e-4e71-a91c-ad31914c43b4", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795017482.785961000, "idStazione": "S01", "idRichiesta": "a7653af7-1575-4db7-93e4-253ac7560650", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795017484.858718300, "idStazione": "S02", "idRichiesta": "3039f53b-4333-4154-9c4f-f393883f9b1a", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795017484.936968500, "idStazione": "S02", "idRichiesta": "b3d7cf59-cc2e-4635-b003-771cc2340d72", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795017486.811197400, "idStazione": "S02", "idRichiesta": "997f5d24-b63a-493b-b221-56563c39d904", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795017486.868493500, "idStazione": "S02", "idRichiesta": "c9335c81-337b-4f1c-b5ec-9f1222a100d9", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795017488.309147200, "idStazione": "S01", "idRichiesta": "74e74a68-0dcf-4f86-8eef-0ad7099e6bcb", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795017488.925197800, "idStazione": "S01", "idRichiesta": "6498f104-180c-42e7-acbb-b0b953b1b593", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795017761.781619800, "idStazione": "S01", "idRichiesta": "a8bb64b5-0c73-468f-910d-b35c4e1dab7d", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795017761.840197700, "idStazione": "S01", "idRichiesta": "0f7f6e3f-624e-40ff-9117-02fd53943dbd", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795017763.937770500, "idStazione": "S02", "idRichiesta": "bf9fcbb4-84e9-44c4-92a6-6057263028fd", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795017763.995842500, "idStazione": "S02", "idRichiesta": "080c0c5e-ac71-468c-9062-dd6384aa65be", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795017764.093035000, "idStazione": "S02", "idRichiesta": "39bea049-bdea-4d45-85f9-85609a768db2", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795017764.180183000, "idStazione": "S02", "idRichiesta": "2de1b14a-6c69-4e6a-b214-0ce7c25f79a4", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795017766.379826100, "idStazione": "S01", "idRichiesta": "3cff6cc6-de58-450d-9dc0-b3a21cdeee63", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795017768.232461100, "idStazione": "S01", "idRichiesta": "ffd3899c-e863-42b5-8185-352363cf5624", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795018028.907869700, "idStazione": "S01", "idRichiesta": "bff07e39-5e27-4129-935c-a27ea522dae1", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795018028.918543500, "idStazione": "S01", "idRichiesta": "c8fddb41-820a-4508-8be1-19877db31da1", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795018030.971091200, "idStazione": "S02", "idRichiesta": "1dc22ee5-0833-4ddb-928c-934911861a0c", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795018031.008897100, "idStazione": "S02", "idRichiesta": "a3a924da-3539-4d30-8792-6409d8afb21d", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795018031.026637300, "idStazione": "S02", "idRichiesta": "547ace41-9b26-420c-984f-0fed19ee8366", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795018031.061030100, "idStazione": "S02", "idRichiesta": "1b85752f-2d5d-48fb-916f-5ffeeaa5923f", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795018033.183307000, "idStazione": "S01", "idRichiesta": "4f4bb0e5-3009-4f8e-81c2-832c0a37525d", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795018034.264562100, "idStazione": "S01", "idRichiesta": "14d32718-bf06-440b-b4ae-549df49c08a7", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795019996.956723000, "idStazione": "S01", "idRichiesta": "54014d9c-8a7e-4118-aa24-d6f26e215657", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795019997.000838600, "idStazione": "S01", "idRichiesta": "4899fd85-9f54-4d49-820c-e7720b81bb03", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795019999.148811300, "idStazione": "S02", "idRichiesta": "e2103198-4e70-48fc-8fcb-19da9b210e7b", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795019999.242275100, "idStazione": "S02", "idRichiesta": "77d24d5e-5fb2-4bf8-a989-59ad16b0e006", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795020000.161456800, "idStazione": "S02", "idRichiesta": "019e7fa3-978c-4537-a09e-f6e7f931905d", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795020000.226380400, "idStazione": "S02", "idRichiesta": "22dfc09a-1ab3-44d4-b991-9d1ea9ae8e9a", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795020001.330761500, "idStazione": "S01", "idRichiesta": "c76d8903-c6aa-435e-b5fa-e846d1b23d5d", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795020002.294951100, "idStazione": "S01", "idRichiesta": "4824f325-0276-415f-a58e-a0dcc7f7c318", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021083.556666200, "idStazione": "S01", "idRichiesta": "cfc2a34d-abd2-4873-a2e6-154a396d2085", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021085.752409500, "idStazione": "S02", "idRichiesta": "0824d4b6-1a3d-4364-a351-068eab3e546b", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021085.873173100, "idStazione": "S02", "idRichiesta": "736660dd-d73f-440e-959c-fc88bc9b5320", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021087.960350700, "idStazione": "S01", "idRichiesta": "584d4bcf-0715-484b-b396-ea23bab2bcc5", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795021094.738515600, "idStazione": "S01", "idRichiesta": "6ede7175-751b-4222-b8c2-33ae80955bd8", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795021096.859564000, "idStazione": "S02", "idRichiesta": "95d1622a-8e9d-4359-8df1-192471cf35a1", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795021096.940359900, "idStazione": "S02", "idRichiesta": "cee1e98d-d148-413b-a86b-b75daf2d8c4b", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795021099.066633300, "idStazione": "S01", "idRichiesta": "0365dee6-5dca-47c6-b1f2-fe251b4209ac", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021128.511106900, "idStazione": "S01", "idRichiesta": "14a1443e-c74b-475e-8374-0724b9999873", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021130.639555300, "idStazione": "S02", "idRichiesta": "9fe86613-0f36-411c-abc5-ea847e087e5a", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021130.765389200, "idStazione": "S02", "idRichiesta": "947bc127-fd62-4daa-824d-034cd05be17f", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021134.697482000, "idStazione": "S01", "idRichiesta": "97ebe07a-b9d3-4ca5-9057-50900692c0aa", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021258.800778100, "idStazione": "S01", "idRichiesta": "b8625dfe-1ab8-4492-be0e-590d974d7635", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021260.992882600, "idStazione": "S02", "idRichiesta": "ca85cf32-cc1e-4926-b994-a5f7fa563229", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021261.135260400, "idStazione": "S02", "idRichiesta": "e4c364af-a045-448e-9b7c-66ddfb73838b", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795021262.080462100, "idStazione": "S01", "idRichiesta": "acde9142-2da9-461f-a4d6-2421964321b2", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795021264.189194200, "idStazione": "S02", "idRichiesta": "4d7050f6-14c8-4210-8784-80f1e5b17eb5", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795021264.279002400, "idStazione": "S02", "idRichiesta": "0013043d-a71d-42f8-9473-558ae5731a5c", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021264.987267500, "idStazione": "S01", "idRichiesta": "2aa53029-15cb-470d-8f62-c9a5185106f3", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795021266.394235600, "idStazione": "S01", "idRichiesta": "4326aac9-7ce6-481c-9611-14d30e439b2f", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021512.112793000, "idStazione": "S01", "idRichiesta": "b92f901a-aec2-4e67-9bdb-1570b6b5486e", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021615.897268800, "idStazione": "S02", "idRichiesta": "9724a54b-374e-47a3-b9bd-7842d44bb54c", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795021616.012157700, "idStazione": "S02", "idRichiesta": "a98e8216-ae03-4545-a744-523a909b98a6", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795021618.108263200, "idStazione": "S01", "idRichiesta": "bc5121a2-0072-458d-955e-9645a3114c09", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795024859.304896600, "idStazione": "S01", "idRichiesta": "ea5dbfc4-a87e-41ee-bf1e-911552df67a6", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795024864.228649800, "idStazione": "S01", "idRichiesta": "a57e19e9-faa4-490e-ab02-11160d4d1532", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795024957.061130400, "idStazione": "S02", "idRichiesta": "fea41795-98aa-408a-bc97-bbceee649d0e", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795024957.088049600, "idStazione": "S02", "idRichiesta": "e0cac2af-a10f-4fee-b563-71091ddec523", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795024957.151682000, "idStazione": "S02", "idRichiesta": "4eb3c454-92cc-4146-ab89-fa68c4dba7ef", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795024957.220806600, "idStazione": "S02", "idRichiesta": "93b8c379-221e-4873-a124-1593ff763a6d", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795024959.300570500, "idStazione": "S01", "idRichiesta": "846df032-1109-4d62-8ea5-6176f152013b", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795024959.378618600, "idStazione": "S01", "idRichiesta": "faa9be7c-5a17-42e5-980d-863fc81c81e7", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795025393.920028500, "idStazione": "S01", "idRichiesta": "d79dcd86-34aa-4192-a563-716146040019", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795025486.959592800, "idStazione": "S02", "idRichiesta": "d5bb2e72-f115-4690-b064-37c70a3e3273", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795025487.051072800, "idStazione": "S02", "idRichiesta": "9144b02f-c0e6-49e3-ac08-c3d1441ca3b8", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795025489.151941300, "idStazione": "S01", "idRichiesta": "ef9617c3-d520-4211-830d-bdea7e76b00b", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795190262.564430100, "idStazione": "S01", "idRichiesta": "63bf3845-41f3-412f-8b63-79c01acdae3f", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795190264.663447700, "idStazione": "S02", "idRichiesta": "2d6e5ebe-8079-4cec-b019-2e74bf5d097a", "tipoMovimento": "INGRESSO"}, {"idTreno": "T002", "timestamp": 1795190264.736981300, "idStazione": "S02", "idRichiesta": "502fab12-043d-4ca1-92ea-7b535dc7441e", "tipoMovimento": "USCITA"}, {"idTreno": "T002", "timestamp": 1795190266.792799000, "idStazione": "S01", "idRichiesta": "5e1b3561-baf0-40c2-9acc-c759243c08d9", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795190287.899150600, "idStazione": "S01", "idRichiesta": "2a12f05a-5fb3-43fe-ad91-d4399d7329e8", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795190291.955123000, "idStazione": "S02", "idRichiesta": "c1de7da1-1943-4f55-8183-46e0f4a41c10", "tipoMovimento": "INGRESSO"}, {"idTreno": "T003", "timestamp": 1795190292.009877500, "idStazione": "S02", "idRichiesta": "b4eb4272-bf19-4c52-82d1-bd87c3f198ae", "tipoMovimento": "USCITA"}, {"idTreno": "T003", "timestamp": 1795190294.073329200, "idStazione": "S01", "idRichiesta": "2504d615-c28c-4c2e-aa9d-2745a5729bba", "tipoMovimento": "INGRESSO"}]}	2026-11-20 17:04:35.204671+00
\.


--
-- Data for Name: storico_transiti; Type: TABLE DATA; Schema: public; Owner: ferrovia
--

COPY public.storico_transiti (id_transito, id_treno, id_stazione, movimento, istante) FROM stdin;
cdfb2292-a179-4b64-bc3a-4037437161bc	T003	S01	USCITA	2026-11-18 11:05:16.164707+00
f16ff5d4-1f45-4ca3-a3c6-9c25e8367d16	T001	S01	USCITA	2026-11-18 11:05:16.259119+00
b5476fb2-80a3-4a82-bb95-32851509929c	T002	S01	USCITA	2026-11-18 11:05:16.274732+00
a47e6adb-7cc3-4c4d-bae8-6c3215b7918d	T003	S02	INGRESSO	2026-11-18 11:05:24.507721+00
001f6622-b431-4bf0-bf3d-c11ae62651d7	T001	S02	INGRESSO	2026-11-18 11:05:24.600264+00
2da94d20-e559-4fb0-b323-6aa711ea86f6	T002	S02	INGRESSO	2026-11-18 11:05:24.616691+00
72f12338-7f69-4ee2-b3c3-a56fbaf038bc	T003	S02	USCITA	2026-11-18 11:05:24.684948+00
09158fc1-621f-4c5c-b650-6e10c2e76cc2	T001	S02	USCITA	2026-11-18 11:05:24.776182+00
ba38874b-a3a3-42e0-8321-fae287dbcb54	T002	S02	USCITA	2026-11-18 11:05:24.867409+00
1a1cd011-48f4-4610-8575-3a592474ac58	T001	S01	INGRESSO	2026-11-18 11:05:27.002113+00
a541d859-03e0-4e29-9f4a-76b81e025afc	T002	S01	INGRESSO	2026-11-18 11:05:27.050721+00
d78785b4-278c-4a3c-a82d-564346b1a6a9	T003	S01	INGRESSO	2026-11-18 11:05:28.47729+00
8230079a-24b9-471b-a588-bc5fa45ed26e	T002	S01	USCITA	2026-11-18 15:19:52.542974+00
36ac7986-6e16-4b1d-9057-9b363d73152c	T003	S01	USCITA	2026-11-18 15:19:52.580205+00
b0eb2e41-1f2d-4720-820f-43cde5ded9f4	T002	S02	INGRESSO	2026-11-18 15:19:54.656634+00
e6cf6068-f151-40a9-99eb-211f1b6338e7	T003	S02	INGRESSO	2026-11-18 15:19:54.690057+00
f39f3f1f-5097-4208-9674-e00e7510e8a4	T002	S02	USCITA	2026-11-18 15:19:54.731322+00
98c05aab-01c7-417b-b0f7-0b9cb3dea80f	T003	S02	USCITA	2026-11-18 15:19:54.812743+00
a2e35f2a-2364-4a8e-a053-6bddfee866a6	T002	S01	INGRESSO	2026-11-18 15:19:56.856114+00
43c3c256-98cd-4b4a-b8ba-7d9b145167e5	T003	S01	INGRESSO	2026-11-18 15:19:58.32885+00
3359019b-927e-4e71-a91c-ad31914c43b4	T002	S01	USCITA	2026-11-18 15:58:02.742423+00
a7653af7-1575-4db7-93e4-253ac7560650	T003	S01	USCITA	2026-11-18 15:58:02.785961+00
3039f53b-4333-4154-9c4f-f393883f9b1a	T002	S02	INGRESSO	2026-11-18 15:58:04.858718+00
b3d7cf59-cc2e-4635-b003-771cc2340d72	T002	S02	USCITA	2026-11-18 15:58:04.936969+00
997f5d24-b63a-493b-b221-56563c39d904	T003	S02	INGRESSO	2026-11-18 15:58:06.811197+00
c9335c81-337b-4f1c-b5ec-9f1222a100d9	T003	S02	USCITA	2026-11-18 15:58:06.868494+00
74e74a68-0dcf-4f86-8eef-0ad7099e6bcb	T002	S01	INGRESSO	2026-11-18 15:58:08.309147+00
6498f104-180c-42e7-acbb-b0b953b1b593	T003	S01	INGRESSO	2026-11-18 15:58:08.925198+00
a8bb64b5-0c73-468f-910d-b35c4e1dab7d	T002	S01	USCITA	2026-11-18 16:02:41.78162+00
0f7f6e3f-624e-40ff-9117-02fd53943dbd	T003	S01	USCITA	2026-11-18 16:02:41.840198+00
bf9fcbb4-84e9-44c4-92a6-6057263028fd	T002	S02	INGRESSO	2026-11-18 16:02:43.937771+00
080c0c5e-ac71-468c-9062-dd6384aa65be	T003	S02	INGRESSO	2026-11-18 16:02:43.995843+00
39bea049-bdea-4d45-85f9-85609a768db2	T002	S02	USCITA	2026-11-18 16:02:44.093035+00
2de1b14a-6c69-4e6a-b214-0ce7c25f79a4	T003	S02	USCITA	2026-11-18 16:02:44.180183+00
3cff6cc6-de58-450d-9dc0-b3a21cdeee63	T002	S01	INGRESSO	2026-11-18 16:02:46.379826+00
ffd3899c-e863-42b5-8185-352363cf5624	T003	S01	INGRESSO	2026-11-18 16:02:48.232461+00
bff07e39-5e27-4129-935c-a27ea522dae1	T003	S01	USCITA	2026-11-18 16:07:08.90787+00
c8fddb41-820a-4508-8be1-19877db31da1	T002	S01	USCITA	2026-11-18 16:07:08.918544+00
1dc22ee5-0833-4ddb-928c-934911861a0c	T003	S02	INGRESSO	2026-11-18 16:07:10.971091+00
a3a924da-3539-4d30-8792-6409d8afb21d	T002	S02	INGRESSO	2026-11-18 16:07:11.008897+00
547ace41-9b26-420c-984f-0fed19ee8366	T003	S02	USCITA	2026-11-18 16:07:11.026637+00
1b85752f-2d5d-48fb-916f-5ffeeaa5923f	T002	S02	USCITA	2026-11-18 16:07:11.06103+00
4f4bb0e5-3009-4f8e-81c2-832c0a37525d	T002	S01	INGRESSO	2026-11-18 16:07:13.183307+00
14d32718-bf06-440b-b4ae-549df49c08a7	T003	S01	INGRESSO	2026-11-18 16:07:14.264562+00
54014d9c-8a7e-4118-aa24-d6f26e215657	T002	S01	USCITA	2026-11-18 16:39:56.956723+00
4899fd85-9f54-4d49-820c-e7720b81bb03	T003	S01	USCITA	2026-11-18 16:39:57.000839+00
e2103198-4e70-48fc-8fcb-19da9b210e7b	T003	S02	INGRESSO	2026-11-18 16:39:59.148811+00
77d24d5e-5fb2-4bf8-a989-59ad16b0e006	T003	S02	USCITA	2026-11-18 16:39:59.242275+00
019e7fa3-978c-4537-a09e-f6e7f931905d	T002	S02	INGRESSO	2026-11-18 16:40:00.161457+00
22dfc09a-1ab3-44d4-b991-9d1ea9ae8e9a	T002	S02	USCITA	2026-11-18 16:40:00.22638+00
c76d8903-c6aa-435e-b5fa-e846d1b23d5d	T003	S01	INGRESSO	2026-11-18 16:40:01.330762+00
4824f325-0276-415f-a58e-a0dcc7f7c318	T002	S01	INGRESSO	2026-11-18 16:40:02.294951+00
cfc2a34d-abd2-4873-a2e6-154a396d2085	T002	S01	USCITA	2026-11-18 16:58:03.556666+00
0824d4b6-1a3d-4364-a351-068eab3e546b	T002	S02	INGRESSO	2026-11-18 16:58:05.75241+00
736660dd-d73f-440e-959c-fc88bc9b5320	T002	S02	USCITA	2026-11-18 16:58:05.873173+00
584d4bcf-0715-484b-b396-ea23bab2bcc5	T002	S01	INGRESSO	2026-11-18 16:58:07.960351+00
6ede7175-751b-4222-b8c2-33ae80955bd8	T003	S01	USCITA	2026-11-18 16:58:14.738516+00
95d1622a-8e9d-4359-8df1-192471cf35a1	T003	S02	INGRESSO	2026-11-18 16:58:16.859564+00
cee1e98d-d148-413b-a86b-b75daf2d8c4b	T003	S02	USCITA	2026-11-18 16:58:16.94036+00
0365dee6-5dca-47c6-b1f2-fe251b4209ac	T003	S01	INGRESSO	2026-11-18 16:58:19.066633+00
14a1443e-c74b-475e-8374-0724b9999873	T002	S01	USCITA	2026-11-18 16:58:48.511107+00
9fe86613-0f36-411c-abc5-ea847e087e5a	T002	S02	INGRESSO	2026-11-18 16:58:50.639555+00
947bc127-fd62-4daa-824d-034cd05be17f	T002	S02	USCITA	2026-11-18 16:58:50.765389+00
97ebe07a-b9d3-4ca5-9057-50900692c0aa	T002	S01	INGRESSO	2026-11-18 16:58:54.697482+00
b8625dfe-1ab8-4492-be0e-590d974d7635	T002	S01	USCITA	2026-11-18 17:00:58.800778+00
ca85cf32-cc1e-4926-b994-a5f7fa563229	T002	S02	INGRESSO	2026-11-18 17:01:00.992883+00
e4c364af-a045-448e-9b7c-66ddfb73838b	T002	S02	USCITA	2026-11-18 17:01:01.13526+00
acde9142-2da9-461f-a4d6-2421964321b2	T003	S01	USCITA	2026-11-18 17:01:02.080462+00
4d7050f6-14c8-4210-8784-80f1e5b17eb5	T003	S02	INGRESSO	2026-11-18 17:01:04.189194+00
0013043d-a71d-42f8-9473-558ae5731a5c	T003	S02	USCITA	2026-11-18 17:01:04.279002+00
2aa53029-15cb-470d-8f62-c9a5185106f3	T002	S01	INGRESSO	2026-11-18 17:01:04.987268+00
4326aac9-7ce6-481c-9611-14d30e439b2f	T003	S01	INGRESSO	2026-11-18 17:01:06.394236+00
b92f901a-aec2-4e67-9bdb-1570b6b5486e	T002	S01	USCITA	2026-11-18 17:05:12.112793+00
9724a54b-374e-47a3-b9bd-7842d44bb54c	T002	S02	INGRESSO	2026-11-18 17:06:55.897269+00
a98e8216-ae03-4545-a744-523a909b98a6	T002	S02	USCITA	2026-11-18 17:06:56.012158+00
bc5121a2-0072-458d-955e-9645a3114c09	T002	S01	INGRESSO	2026-11-18 17:06:58.108263+00
ea5dbfc4-a87e-41ee-bf1e-911552df67a6	T002	S01	USCITA	2026-11-18 18:00:59.304897+00
a57e19e9-faa4-490e-ab02-11160d4d1532	T003	S01	USCITA	2026-11-18 18:01:04.22865+00
fea41795-98aa-408a-bc97-bbceee649d0e	T003	S02	INGRESSO	2026-11-18 18:02:37.06113+00
e0cac2af-a10f-4fee-b563-71091ddec523	T002	S02	INGRESSO	2026-11-18 18:02:37.08805+00
4eb3c454-92cc-4146-ab89-fa68c4dba7ef	T003	S02	USCITA	2026-11-18 18:02:37.151682+00
93b8c379-221e-4873-a124-1593ff763a6d	T002	S02	USCITA	2026-11-18 18:02:37.220807+00
846df032-1109-4d62-8ea5-6176f152013b	T003	S01	INGRESSO	2026-11-18 18:02:39.300571+00
faa9be7c-5a17-42e5-980d-863fc81c81e7	T002	S01	INGRESSO	2026-11-18 18:02:39.378619+00
d79dcd86-34aa-4192-a563-716146040019	T002	S01	USCITA	2026-11-18 18:09:53.920029+00
d5bb2e72-f115-4690-b064-37c70a3e3273	T002	S02	INGRESSO	2026-11-18 18:11:26.959593+00
9144b02f-c0e6-49e3-ac08-c3d1441ca3b8	T002	S02	USCITA	2026-11-18 18:11:27.051073+00
ef9617c3-d520-4211-830d-bdea7e76b00b	T002	S01	INGRESSO	2026-11-18 18:11:29.151941+00
63bf3845-41f3-412f-8b63-79c01acdae3f	T002	S01	USCITA	2026-11-20 15:57:42.56443+00
2d6e5ebe-8079-4cec-b019-2e74bf5d097a	T002	S02	INGRESSO	2026-11-20 15:57:44.663448+00
502fab12-043d-4ca1-92ea-7b535dc7441e	T002	S02	USCITA	2026-11-20 15:57:44.736981+00
5e1b3561-baf0-40c2-9acc-c759243c08d9	T002	S01	INGRESSO	2026-11-20 15:57:46.792799+00
2a12f05a-5fb3-43fe-ad91-d4399d7329e8	T003	S01	USCITA	2026-11-20 15:58:07.899151+00
c1de7da1-1943-4f55-8183-46e0f4a41c10	T003	S02	INGRESSO	2026-11-20 15:58:11.955123+00
b4eb4272-bf19-4c52-82d1-bd87c3f198ae	T003	S02	USCITA	2026-11-20 15:58:12.009878+00
2504d615-c28c-4c2e-aa9d-2745a5729bba	T003	S01	INGRESSO	2026-11-20 15:58:14.073329+00
\.


--
-- Name: railway_state_snapshot railway_state_snapshot_pkey; Type: CONSTRAINT; Schema: public; Owner: ferrovia
--

ALTER TABLE ONLY public.railway_state_snapshot
    ADD CONSTRAINT railway_state_snapshot_pkey PRIMARY KEY (id);


--
-- Name: storico_transiti storico_transiti_pkey; Type: CONSTRAINT; Schema: public; Owner: ferrovia
--

ALTER TABLE ONLY public.storico_transiti
    ADD CONSTRAINT storico_transiti_pkey PRIMARY KEY (id_transito);


--
-- Name: idx_storico_transiti_stazione; Type: INDEX; Schema: public; Owner: ferrovia
--

CREATE INDEX idx_storico_transiti_stazione ON public.storico_transiti USING btree (id_stazione, istante);


--
-- Name: idx_storico_transiti_treno; Type: INDEX; Schema: public; Owner: ferrovia
--

CREATE INDEX idx_storico_transiti_treno ON public.storico_transiti USING btree (id_treno, istante);


--
-- PostgreSQL database dump complete
--

\unrestrict 8LYIZJftZsVgf43hwxl4ehdoGrHwWO1uDRUvf7EtSgu7XhSrWsoypwpGLJXWUpM

