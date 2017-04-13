import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

public class Linker {

    private static int MACHINE_SIZE = 200;
    private int memoryMapSize;
    private Map<String, Integer> symbolTable = new LinkedHashMap<>();
    private Map<String, Integer> symbolToModule = new LinkedHashMap<>();
    private Map<Integer, Integer> moduleSizes = new HashMap<>();
    private Map<String, List<String>> symbolTableErrors = new HashMap<>();
    private Map<Integer, List<String>> memoryMapErrors = new HashMap<>();
    private List<String> trailingErrors = new ArrayList<>();
    private Set<String> usedSymbols = new HashSet<>();
    private int[] memoryMap;

    public void firstPass(String filePath) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(filePath));
        int numModules = sc.nextInt();
        int baseAddr = 0;

        for (int i = 1; i <= numModules; i++) {
            Map<String, Integer> partialSym = readDefinitions(sc);
            skipOverUses(sc);
            int moduleSize = sc.nextInt();
            skipOverText(sc, moduleSize);

            assignSymbolsForModule(partialSym, baseAddr, moduleSize, i);

            moduleSizes.put(i, moduleSize);
            baseAddr += moduleSize;
        }
        memoryMapSize = baseAddr;
    }

    private void assignSymbolsForModule(Map<String, Integer> partialSym, int baseAddr, int moduleSize, int moduleNum) {
        for (String symbol : partialSym.keySet()) {
            if (!symbolTableErrors.containsKey(symbol))
                symbolTableErrors.put(symbol, new ArrayList<String>());

            if (symbolTable.containsKey(symbol)) {
                symbolTableErrors.get(symbol).add("Error: This variable is multiply defined; first value used.");
            }
            else {
                int relAddr = partialSym.get(symbol);
                if (relAddr >= moduleSize) {
                    symbolTableErrors.get(symbol).add("Error: Definition exceeds module size; first word in module used.");
                    relAddr = 0;
                }
                symbolTable.put(symbol, baseAddr + relAddr);
                symbolToModule.put(symbol, moduleNum);
            }
        }
    }

    private void secondPass(String filePath) throws FileNotFoundException {
        Scanner sc = new Scanner(new File(filePath));
        memoryMap = new int[memoryMapSize];

        int numModules = sc.nextInt();
        int baseAddr = 0;

        for (int i = 1; i <= numModules; i++) {
            skipOverDefinitions(sc);
            int moduleSize = moduleSizes.get(i);
            Map<Integer, String> symbolUsage = readUses(sc, baseAddr, moduleSize, i);
            readText(sc, baseAddr, symbolUsage);
            baseAddr += moduleSize;
        }
        checkSymbolUsage();
    }

    private void checkSymbolUsage() {
        for (String symbol : symbolToModule.keySet()) {
            if (!usedSymbols.contains(symbol)) {
                trailingErrors.add("Warning: " + symbol + " was defined in module " + symbolToModule.get(symbol) + " but never used.");
            }
        }
    }

    private void readText(Scanner sc, int baseAddr, Map<Integer, String> symbolUsage) {
        int numText = sc.nextInt();
        for (int i = 0; i < numText; i++) {
            char type = sc.next().charAt(0);
            String word = sc.next();
            int opCode = word.charAt(0) - '0', addr = Integer.parseInt(word.substring(1));
            int absAddr = baseAddr + i;
            if (type == 'I')
                memoryMap[absAddr] = Integer.parseInt(word);
            else if (type == 'A') {
                if (Integer.parseInt(word.substring(1)) >= MACHINE_SIZE) {
                    initErrorForMemoryLoc(absAddr);
                    memoryMapErrors.get(absAddr).add("Error: Absolute address exceeds machine size; zero used.");
                    memoryMap[absAddr] = opCode * 1000;
                }
                else
                    memoryMap[absAddr] = Integer.parseInt(word);
            }
            else if (type == 'R') {
                if (addr >= numText) {
                    initErrorForMemoryLoc(absAddr);
                    memoryMapErrors.get(absAddr).add("Error: Relative address exceeds module size; zero used.");
                    memoryMap[absAddr] = opCode * 1000;
                }
                else
                    memoryMap[absAddr] = opCode * 1000 + baseAddr + addr;
            }
            else if (type == 'E') {
                String symbol = symbolUsage.get(absAddr);
                if (!symbolTable.containsKey(symbol)) {
                    initErrorForMemoryLoc(absAddr);
                    memoryMapErrors.get(absAddr).add("Error: " + symbol + " is not defined; zero used.");
                    memoryMap[absAddr] = opCode * 1000;
                }
                else
                    memoryMap[absAddr] = opCode * 1000 + symbolTable.get(symbol);
            }
        }
    }

    private Map<Integer, String> readUses(Scanner sc, int baseAddr, int moduleSize, int moduleNum) {
        Map<Integer, String> symbolUsage = new HashMap<>();
        int numUses = sc.nextInt();
        for (int i = 0; i < numUses; i++) {
            String symbol = sc.next();
            int relAddr;
            while ((relAddr = sc.nextInt()) != -1) {
               usedSymbols.add(symbol);
               if (relAddr >= moduleSize)
                   trailingErrors.add("Error: Use of " + symbol + " in module " + moduleNum + " exceeds module size; use ignored.");
               else {
                   int absAddr = baseAddr + relAddr;
                   if (symbolUsage.containsKey(absAddr)) {
                       initErrorForMemoryLoc(absAddr);
                       memoryMapErrors.get(absAddr).add("Error: Multiple variables used in instruction; all but first ignored.");
                   }
                   else {
                       symbolUsage.put(absAddr, symbol);
                   }
               }
            }
        }
        return symbolUsage;
    }

    private void initErrorForMemoryLoc(int absAddr) {
        if (!memoryMapErrors.containsKey(absAddr))
            memoryMapErrors.put(absAddr, new ArrayList<String>());
    }

    private void skipOverDefinitions(Scanner sc) {
        int numDefs = sc.nextInt();
        for (int i = 0; i < numDefs; i++) {
            sc.next();
            sc.nextInt();
        }
    }

    private void skipOverText(Scanner sc, int moduleSize) {
        for (int i = 0; i < moduleSize; i++) {
            sc.next();
            sc.next();
        }
    }

    private void skipOverUses(Scanner sc) {
        int numUses = sc.nextInt();
        for (int i = 0; i < numUses; i++) {
            sc.next();
            while (sc.nextInt() != -1) {}
        }
    }

    private Map<String, Integer> readDefinitions(Scanner sc) {
        Map<String, Integer> partialSymTable = new LinkedHashMap<>();
        int numDefs = sc.nextInt();
        for (int i = 0; i < numDefs; i++) {
            partialSymTable.put(sc.next(), sc.nextInt());
        }
        return partialSymTable;
    }

    public static void main(String[] args) throws FileNotFoundException {
        Linker linker = new Linker();
        linker.firstPass(args[0]);
        linker.printSymbolTable();
        System.out.println();
        linker.secondPass(args[0]);
        linker.printMemoryMap();
        System.out.println();
        linker.printTrailingErrors();
    }

    private void printTrailingErrors() {
        for (String error: trailingErrors) {
            System.out.println(error);
        }
    }

    private void printMemoryMap() {
        System.out.println("Memory Map");
        for (int i = 0; i < memoryMap.length; i++) {
            System.out.print(String.valueOf(i) + ": " + String.valueOf(memoryMap[i]));
            if (memoryMapErrors.containsKey(i)) {
                for (String error : memoryMapErrors.get(i)) {
                    System.out.print(" " + error);
                }
            }
            System.out.println();
        }
    }

    private void printSymbolTable() {
        System.out.println("Symbol Table");
        for (String symbol : symbolTable.keySet()) {
            System.out.print(symbol + "=" + symbolTable.get(symbol));
            for (String error : symbolTableErrors.get(symbol)) {
                System.out.print(" " + error);
            }
            System.out.println();
        }
    }
}