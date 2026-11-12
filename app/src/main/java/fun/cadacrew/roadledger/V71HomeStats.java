package fun.cadacrew.roadledger;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Six calendar-month buckets for a selected vehicle. Independent of Android for repeatable tests. */
public final class V71HomeStats {
    public static final int MONTH_COUNT = 6;
    public static final class Month {
        public final long startInclusive, endExclusive;
        public final String label;
        public double fuel, expenses, services, tripKm;
        Month(long start, long end) {
            startInclusive = start;
            endExclusive = end;
            label = new SimpleDateFormat("MMM", Locale.US).format(new Date(start));
        }
        public double totalSpend() { return fuel + expenses + services; }
    }
    private final Month[] months = new Month[MONTH_COUNT];

    public V71HomeStats(long nowMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(nowMillis);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.MONTH, -(MONTH_COUNT - 1));
        for (int i = 0; i < MONTH_COUNT; i++) {
            long start = calendar.getTimeInMillis();
            calendar.add(Calendar.MONTH, 1);
            months[i] = new Month(start, calendar.getTimeInMillis());
        }
    }

    /** Returns oldest to newest; each entry belongs to this snapshot. */
    public Month[] months() { return months.clone(); }
    public double currentMonthSpend() { return months[MONTH_COUNT - 1].totalSpend(); }
    public double currentMonthTripKm() { return months[MONTH_COUNT - 1].tripKm; }
    public double highestMonthSpend() {
        double max = 0;
        for (Month m : months) max = Math.max(max, m.totalSpend());
        return max;
    }
    public void addFuel(long at, double netCost) { double amount=nonnegative(netCost); Month m=bucket(at); if (m!=null)m.fuel+=amount; }
    public void addExpense(long at, double cost) { double amount=nonnegative(cost); Month m=bucket(at); if (m!=null)m.expenses+=amount; }
    public void addService(long at, double cost) { double amount=nonnegative(cost); Month m=bucket(at); if (m!=null)m.services+=amount; }
    public void addTrip(long at, double distanceKm) { double amount=nonnegative(distanceKm); Month m=bucket(at); if (m!=null)m.tripKm+=amount; }
    private Month bucket(long at) {
        for (Month m : months) if (at >= m.startInclusive && at < m.endExclusive) return m;
        return null;
    }
    private static double nonnegative(double value) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException("Invalid amount");
        return value;
    }
}
