import java.io.IOException;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileReader;
import com.gurobi.gurobi.*;

public class Progetto {

    private static GRBEnv ambiente;
    private static int numeroRottami; 
    private static int numeroElementi; 
    private static int kgAcciaio;
    private static int pediceR;
    private static int pediceE;
    private static double zMassimo;
    private static double[] prezzoUnitario;
    private static double[] coefficienteFusione;
    private static double[] betaMinimo;
    private static double[] betaMassimo;
    private static double[][] quantitaElementoPerRottame;

    public static void main(String[] args) {
        leggiDati();

        System.out.println("GRUPPO N"); //TODO: Cambiare con il proprio gruppo o singolo
        System.out.println("Componenti: MEMBRO1 MEMBRO2"); //TODO: Cambiare con i propri cognomi

        try {
            ambiente = new GRBEnv();
            ambiente.set(GRB.IntParam.LogToConsole, 0);
            GRBModel modello = primaDomanda();
            secondaDomanda(modello);
            terzaDomanda(modello);
        } catch (GRBException e) {
            e.printStackTrace();
        }
    }

    private static void leggiDati() {
        try (BufferedReader lettore = new BufferedReader(new FileReader("./data/Coppia_N.txt"))) { //TODO: Cambiare con il proprio file di input
            numeroRottami = Integer.parseInt(lettore.readLine().split("[\\t ]+")[1]);
            numeroElementi = Integer.parseInt(lettore.readLine().split("[\\t ]+")[1]);
            kgAcciaio = Integer.parseInt(lettore.readLine().split("[\\t ]+")[1]);
            lettore.readLine();
            lettore.readLine();
            prezzoUnitario = leggiArrayDouble(lettore, numeroRottami);
            lettore.readLine();
            lettore.readLine();
            coefficienteFusione = leggiArrayDouble(lettore, numeroRottami);
            lettore.readLine();
            lettore.readLine();
            betaMinimo = leggiArrayDouble(lettore, numeroElementi);
            lettore.readLine();
            lettore.readLine();
            betaMassimo = leggiArrayDouble(lettore, numeroElementi);
            lettore.readLine();
            lettore.readLine();
            quantitaElementoPerRottame = new double[numeroRottami][numeroElementi];
            for (int i = 0; i < numeroRottami; i++) {
                quantitaElementoPerRottame[i] = leggiArrayDouble(lettore, numeroElementi);
            }
            lettore.readLine();
            lettore.readLine();
            zMassimo = Double.parseDouble(lettore.readLine().split("[\\t ]+")[1]);
            pediceR = Integer.parseInt(lettore.readLine().split("[\\t ]+")[1]);
            pediceE = Integer.parseInt(lettore.readLine().split("[\\t ]+")[1]);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private static double[] leggiArrayDouble(BufferedReader lettore, int dimensione) throws IOException {
        String riga = lettore.readLine();
        while (riga.trim().isEmpty()) {
            riga = lettore.readLine();
        }
        String[] parti = riga.split("[\\t ]+");
        double[] risultato = new double[dimensione];
        for (int i = 0; i < dimensione; i++) {
            risultato[i] = Double.parseDouble(parti[i]);
        }
        return risultato;
    }

    public static GRBModel primaDomanda() throws GRBException {
        
        GRBModel modello = new GRBModel(ambiente);
        GRBVar[] variabiliObiettivo = new GRBVar[numeroRottami];
        for (int i = 0; i < numeroRottami; i++) {
            variabiliObiettivo[i] = modello.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "x_" + (i + 1));
        }
        GRBLinExpr obiettivo = new GRBLinExpr();
        for (int i = 0; i < numeroRottami; i++) {
            obiettivo.addTerm(prezzoUnitario[i], variabiliObiettivo[i]);
        }
        modello.setObjective(obiettivo, GRB.MINIMIZE);
        GRBLinExpr vincoloProduzioneAcciaio = new GRBLinExpr();
        for (int i = 0; i < numeroRottami; i++) {
            vincoloProduzioneAcciaio.addTerm(coefficienteFusione[i] / 100, variabiliObiettivo[i]);
        }
        modello.addConstr(vincoloProduzioneAcciaio, GRB.EQUAL, kgAcciaio, "Q");
        for (int j = 0; j < numeroElementi; j++) {
            GRBLinExpr vincoloElemento = new GRBLinExpr();
            for (int i = 0; i < numeroRottami; i++) {
                vincoloElemento.addTerm((quantitaElementoPerRottame[i][j] / 100) * (coefficienteFusione[i] / 100), variabiliObiettivo[i]);
            }
            modello.addConstr(vincoloElemento, GRB.GREATER_EQUAL, (betaMinimo[j] / 100) * kgAcciaio, "minimo_" + (j + 1));
            modello.addConstr(vincoloElemento, GRB.LESS_EQUAL, (betaMassimo[j] / 100) * kgAcciaio, "massimo_" + (j + 1));
        }

        modello.optimize();
        if (modello.get(GRB.IntAttr.Status) == GRB.OPTIMAL) {
            GRBConstr[] variabiliSlack = modello.getConstrs();
            double valoreFunzioneObiettivo = modello.get(GRB.DoubleAttr.ObjVal);
            
            int[] baseVariabili = modello.get(GRB.IntAttr.VBasis, variabiliObiettivo);
            int[] baseVincoli = modello.get(GRB.IntAttr.CBasis, variabiliSlack);
            StringBuilder sbBase = new StringBuilder();
            sbBase.append("[");
            for (int i = 0; i < numeroRottami; i++) {
                sbBase.append(baseVariabili[i] == GRB.BASIC ? "1, " : "0, ");
            }
            sbBase.setLength(sbBase.length() - 2);
            sbBase.append("]");
            String risultatoBase = sbBase.toString();
            
            StringBuilder sbCostiRidotti = new StringBuilder();
            sbCostiRidotti.append("[");
            double[] costiRidottiVariabili = modello.get(GRB.DoubleAttr.RC, variabiliObiettivo);
            for (int i = 0; i < numeroRottami; i++) {
                sbCostiRidotti.append(String.format("%.4f", costiRidottiVariabili[i])).append(", ");
            }
            sbCostiRidotti.setLength(sbCostiRidotti.length() - 2);
            sbCostiRidotti.append("]");
            String risultatoCostiRidotti = sbCostiRidotti.toString();
            
            boolean degenere = false;
            for (int i = 0; i < numeroRottami; i++) {
                if (baseVariabili[i] == GRB.BASIC && variabiliObiettivo[i].get(GRB.DoubleAttr.X) == 0) {
                    degenere = true;
                    break;
                }
            } 
            if (!degenere) {
                for (int i = 0; i < variabiliSlack.length; i++) {
                    if (baseVincoli[i] == GRB.BASIC && variabiliSlack[i].get(GRB.DoubleAttr.Slack) == 0) {
                        degenere = true;
                        break;
                    }
                }
            }
            
            boolean soluzioniMultiple = false;
            for (int i = 0; i < numeroRottami; i++) {
                if (baseVariabili[i] != GRB.BASIC && variabiliObiettivo[i].get(GRB.DoubleAttr.RC) == 0) {
                    soluzioniMultiple = true;
                    break;
                }
            }
            if (!soluzioniMultiple) {
                for (int i = 0; i < variabiliSlack.length; i++) {
                    if (baseVincoli[i] != GRB.BASIC && variabiliSlack[i].get(GRB.DoubleAttr.Pi) == 0) {
                        soluzioniMultiple = true;
                        break;
                    }
                }
            }
            
            ArrayList<String> vincoliAttivi = new ArrayList<>();
            double[] valoriSlack = modello.get(GRB.DoubleAttr.Slack, variabiliSlack);
            for (int i = 0; i < valoriSlack.length; i++) {
                if (valoriSlack[i] == 0) {
                    vincoliAttivi.add(variabiliSlack[i].get(GRB.StringAttr.ConstrName));
                }
            }
            
            int variabiliObiettivoNonZero = 0;
            for (GRBVar var : variabiliObiettivo) {
                if (var.get(GRB.DoubleAttr.X) != 0) {
                    variabiliObiettivoNonZero++;
                }
            }
            int variabiliSlackNonZero = 0;
            for (double var : valoriSlack) {
                if (var != 0) {
                    variabiliSlackNonZero++;
                }
            }
            
            int componentiDuali = variabiliSlackNonZero + variabiliObiettivoNonZero;
            System.out.println("QUESITO I:");
            System.out.printf("funzione obiettivo = %.4f\n", valoreFunzioneObiettivo);
            System.out.println("variabili in base: " + risultatoBase);
            System.out.println("coefficienti di costo ridotto: " + risultatoCostiRidotti);
            System.out.println("degenere: " + (degenere ? "si" : "no"));
            System.out.println("multipla: " + (soluzioniMultiple ? "si" : "no"));
            System.out.println("vincoli attivi: " + vincoliAttivi);
            System.out.println("componenti duale: " + componentiDuali);
            System.out.println("\n");
        }
        return modello;
    }

    public static void secondaDomanda(GRBModel modello) throws GRBException {
        GRBVar variabilePedice = modello.getVarByName("x_" + (pediceR));
        double prMin = variabilePedice.get(GRB.DoubleAttr.SAObjLow);
        double prMax = variabilePedice.get(GRB.DoubleAttr.SAObjUp);
        String deltaPr = "[" + (prMin <= - 10000000000.0 ? "-INF" : String.format("%.4f", prMin)) + ", " + (prMax >= 10000000000.0 ? "+INF" : String.format("%.4f", prMax)) + "]";
        
        GRBConstr vincoloElemento = modello.getConstrByName("massimo_" + (pediceE));
        double eMin = (vincoloElemento.get(GRB.DoubleAttr.SARHSLow) * 100) / kgAcciaio;
        double eMax = (vincoloElemento.get(GRB.DoubleAttr.SARHSUp) * 100) / kgAcciaio;
        String deltaE = "[" + (eMin <= - 10000000000.0 ? "-INF" : String.format("%.4f", eMin)) + ", " + (eMax >= 10000000000.0 ? "+INF" : String.format("%.4f", eMax)) + "]";
        
        double valoreObiettivoCorrente = modello.get(GRB.DoubleAttr.ObjVal);
        double risultatoZMassimo = (kgAcciaio * zMassimo) / valoreObiettivoCorrente;
        
        System.out.println("QUESITO II:");
        System.out.println("variazione pr_" + pediceR + " = "+ deltaPr);
        System.out.println("variazione bmax_e_" + pediceE + " = " + deltaE);
        System.out.println("Q = " + String.format("%.4f", risultatoZMassimo));
        System.out.println("\n");
    }

    public static void terzaDomanda(GRBModel modello) throws GRBException {
        GRBModel modelloAlternativo = new GRBModel(modello);
        modelloAlternativo.remove(modelloAlternativo.getConstrByName("Q"));
        GRBLinExpr obiettivoArtificiale = new GRBLinExpr();
        GRBVar variabileArtificiale = modelloAlternativo.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS, "variabileArtificiale");
        obiettivoArtificiale.addTerm(1, variabileArtificiale);
        modelloAlternativo.setObjective(obiettivoArtificiale, GRB.MINIMIZE);
        GRBVar[] variabiliObiettivo = modello.getVars();
        GRBLinExpr vincoloProduzioneAcciaio = new GRBLinExpr();
        for (int i = 0; i < numeroRottami; i++) {
            vincoloProduzioneAcciaio.addTerm(coefficienteFusione[i] / 100, variabiliObiettivo[i]);
        }
        vincoloProduzioneAcciaio.addTerm(1, variabileArtificiale);
        modelloAlternativo.addConstr(vincoloProduzioneAcciaio, GRB.EQUAL, kgAcciaio, "Q");

        modelloAlternativo.update();
        modelloAlternativo.optimize();
        double valoreFunzioneObiettivo = 0.0;
        for (int i = 0; i < numeroRottami; i++) {
            valoreFunzioneObiettivo += prezzoUnitario[i] * modelloAlternativo.getVar(i).get(GRB.DoubleAttr.X);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        GRBVar[] variabiliObiettivoAlternative = modelloAlternativo.getVars();
        for (int i = 0; i < numeroRottami; i++) {
            sb.append(String.format("%.4f", variabiliObiettivoAlternative[i].get(GRB.DoubleAttr.X))).append(", ");
        }
        sb.setLength(sb.length() - 2);
        sb.append("]");
        String valoriVariabili = sb.toString();

        System.out.println("QUESITO III:");
        System.out.println("funzione obiettivo = " + String.format("%.4f", valoreFunzioneObiettivo));
        System.out.print("valore variabili: " + valoriVariabili);        
    }
}