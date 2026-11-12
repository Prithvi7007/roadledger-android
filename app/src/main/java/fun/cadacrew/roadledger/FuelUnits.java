package fun.cadacrew.roadledger;

/** Unit and energy-family conversions. Native storage uses L, kWh, kg or m3.
 * Never convert kg to m3 without a measured gas density. */
public final class FuelUnits {
    private FuelUnits() {}
    public static final String[] TYPES={"Gasoline","Diesel","Ethanol","LPG","CNG","LNG","Flex-fuel","Electric","Disabled"};
    public static final String[] ECONOMY={"Auto","L/100 km","km/L","US MPG","UK MPG","kWh/100 km","kg/100 km","m3/100 km"};
    public static final String[] DISTANCES={"Kilometres","Miles"};
    public static String[] unitsFor(String fuel){
        if("Disabled".equals(fuel))return new String[]{"L"};
        if("Electric".equals(fuel))return new String[]{"kWh"};
        if("CNG".equals(fuel)||"LNG".equals(fuel))return new String[]{"kg","m3"};
        return new String[]{"L","US gal","UK gal"};
    }
    public static boolean contains(String[] values,String item){for(String v:values)if(v.equals(item))return true;return false;}
    public static String baseUnit(String unit){
        if("L".equals(unit)||"US gal".equals(unit)||"UK gal".equals(unit))return "L";
        if("kWh".equals(unit)||"kg".equals(unit)||"m3".equals(unit))return unit;
        throw new IllegalArgumentException("Unknown quantity unit: "+unit);
    }
    public static double factor(String unit){
        switch(unit){
            case "L": case "kg": case "kWh": case "m3":return 1d;
            case "US gal":return 3.785411784d;
            case "UK gal":return 4.54609d;
            default:throw new IllegalArgumentException("Unknown quantity unit: "+unit);
        }
    }
    public static double toBase(double quantity,String unit){
        if(!Double.isFinite(quantity)||quantity<=0)throw new IllegalArgumentException("Quantity must be positive.");
        return quantity*factor(unit);
    }
    public static double fromBase(double quantity,String unit){return quantity/factor(unit);}
    public static void checkFuelUnit(String fuel,String unit){
        if(!contains(TYPES,fuel)||!contains(unitsFor(fuel),unit))
            throw new IllegalArgumentException("Fuel type and quantity unit do not match.");
    }
    public static String consumptionUnit(String base,String configured,int distanceSystem){
        if(!contains(ECONOMY,configured))configured="Auto";
        if("L".equals(base)){
            if(contains(new String[]{"L/100 km","km/L","US MPG","UK MPG"},configured))return configured;
            return distanceSystem==1?"US MPG":"L/100 km";
        }
        if("kWh".equals(base))return "kWh/100 km";
        if("kg".equals(base))return "kg/100 km";
        return "m3/100 km";
    }
    public static double displayConsumption(double basePer100Km,String base,String display,int distanceSystem){
        if(!Double.isFinite(basePer100Km)||basePer100Km<=0)return Double.NaN;
        String target=consumptionUnit(base,display,distanceSystem);
        switch(target){
            case "km/L":return 100d/basePer100Km;
            case "US MPG":return 235.214583d/basePer100Km;
            case "UK MPG":return 282.480936d/basePer100Km;
            default:return basePer100Km;
        }
    }
}
