package jdk;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/// Wielowejściowa blokada wyłączna ([Lock]) zbudowana z dwóch [VarHandle], kolejki i [LockSupport]:
/// części, które [ReentrantLock] chowa w `AbstractQueuedSynchronizer`, rozpisane na wierzchu.
///
/// Istnieje, żeby odpowiedzieć kodem, a nie prozą, na jedno pytanie: _co właściwie jest w blokadzie?_
/// Odpowiedź jest mniejsza, niż się wydaje.
///
/// 1. **Jedno atomowe słowo stanu.** `state` to liczba zajęć: `0` znaczy wolna, a udane
///    `compareAndSet(0, 1)` _jest_ zajęciem. Nic więcej na szybkiej ścieżce się nie liczy:
///    blokada bez rywalizacji to jeden CAS i jeden zwykły zapis.
///
/// 1. **Właściciel, żeby dało się liczyć zajęcia.** To cała wielowejściowość i cały powód, dla
///    którego `unlock` może odrzucić wątek, który nigdy blokady nie zajął: różnica między blokadą a
///    zezwoleniem [java.util.concurrent.Semaphore].
///
/// 1. **Kolejka i protokół parkowania** na wypadek, gdy CAS się nie uda. Tu mieszka wszystko, co
///    trudne: wątek musi stanąć w kolejce, sprawdzić ponownie, zaparkować i zostać obudzony
///    dokładnie tyle razy, ile trzeba.
///
/// Domyślny konstruktor daje blokadę, w której nowy wątek może zająć wolną blokadę przed czekającymi
/// (barging); `new VarHandleLock(true)` daje kolejność przybycia. [#tryLock()] zawsze omija kolejkę,
/// z tym samym udokumentowanym wyjątkiem co `ReentrantLock` i `Semaphore`.
///
/// **Dlaczego VarHandle, a nie `volatile` i `synchronized`.** Pole `volatile` daje każdemu dostępowi
/// to samo, najsilniejsze uporządkowanie. `VarHandle` czyni uporządkowanie własnością _dostępu_, więc
/// każda linia może powiedzieć, czego potrzebuje:
///
/// - `STATE.compareAndSet(this, 0, 1)`: zajęcie. Atomowe i z pełną barierą, bo wszystko, co zrobił
///    poprzedni właściciel, musi być widoczne, zanim cokolwiek zrobi ten.
///
/// - `STATE.set(this, n)` przy ponownym wejściu: **zwykły** zapis. Do tej linii dociera tylko
///    właściciel, a żaden inny wątek nie może czytać licznika, więc uporządkowanie nic by nie dało.
///
/// - `STATE.setVolatile(this, 0)`: zwolnienie. Ten jeden zapis publikuje całą sekcję krytyczną
///    następnemu, kto zajmie blokadę.
///
/// Zwykły dostęp na ścieżce ponownego wejścia to sedno ćwiczenia, a nie mikrooptymalizacja: jest
/// poprawny tylko dzięki zapisanemu niezmiennikowi (pole należy do właściciela, dopóki blokada jest
/// zajęta), a zapisanie go czyni niezmiennik widocznym.
///
/// **Zgubione pobudki i dlaczego tutaj ich nie ma.** Niebezpieczny przeplot: wątek przegrywa CAS, a
/// właściciel zwalnia blokadę i budzi kolejkę, _zanim_ ten wątek do niej trafi. Wątek parkuje wtedy i
/// nikt go już nie obudzi. Ochroną jest kolejność, nie szczęście: `acquireQueued` **najpierw** staje
/// w kolejce, a **potem** sprawdza ponownie, więc zwolnienie, które przegapiło wejście do kolejki,
/// zawsze złapie ponowne sprawdzenie, a zwolnienie, które je widziało, zawsze obudzi. Resztę robi
/// [LockSupport]: jego zezwolenie się zachowuje, więc `unpark` przed `park` nie ginie, tylko sprawia,
/// że `park` od razu wraca.
///
/// **Czego nie robi.** [#newCondition()] rzuca wyjątek: poprawny [Condition] wymaga drugiej kolejki i
/// protokołu przenoszenia między nimi, co jest większym ćwiczeniem niż to, a
/// [java.util.concurrent.locks.StampedLock] odmawia z tego samego powodu. Do prawdziwego użytku
/// weź `ReentrantLock`: jest lepiej przetestowany, ma warunki i (jak pokazują pomiary w notatce) jest
/// też szybszy przy rywalizacji.
///
/// @see java.util.concurrent.locks.ReentrantLock
public final class VarHandleLock implements Lock {

    private static final VarHandle STATE;
    private static final VarHandle OWNER;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            STATE = lookup.findVarHandle(VarHandleLock.class, "state", int.class);
            OWNER = lookup.findVarHandle(VarHandleLock.class, "owner", Thread.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);   // klasa jest bezużyteczna; przerwij przy ładowaniu
        }
    }

    private int state;

    private Thread owner;

    private final Queue<Thread> waiters = new ConcurrentLinkedQueue<>();

    private final boolean fair;

    public VarHandleLock() {
        this(false);
    }

    public VarHandleLock(boolean fair) {
        this.fair = fair;
    }

    // ---------- szybka ścieżka: jeden CAS ----------

    private boolean tryAcquire() {
        Thread me = Thread.currentThread();
        if (OWNER.getVolatile(this) == me) {              // ponowne wejście: już jesteśmy właścicielem
            STATE.set(this, (int) STATE.get(this) + 1);   // zwykły zapis: pole należy do właściciela, dopóki blokada jest zajęta
            return true;
        }
        if (STATE.compareAndSet(this, 0, 1)) {
            OWNER.setVolatile(this, me);
            return true;
        }
        return false;
    }

    @Override
    public boolean tryLock() {
        // celowo omija kolejkę, dokładnie jak ReentrantLock.tryLock(), nawet w trybie sprawiedliwym
        return tryAcquire();
    }

    @Override
    public void lock() {
        if (canBarge() && tryAcquire()) return;
        try {
            acquireQueued(false, 0L);
        } catch (InterruptedException impossible) {
            throw new AssertionError("uninterruptible acquire threw", impossible);
        }
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        if (canBarge() && tryAcquire()) return;
        acquireQueued(true, 0L);
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        if (Thread.interrupted()) throw new InterruptedException();
        if (canBarge() && tryAcquire()) return true;
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        if (deadline == 0L) deadline = 1L;                // 0 to znacznik „bez terminu”
        return acquireQueued(true, deadline);
    }

    private boolean canBarge() {
        return !fair || waiters.isEmpty() || OWNER.getVolatile(this) == Thread.currentThread();
    }

    // ---------- wolna ścieżka: kolejka, ponowne sprawdzenie, parkowanie ----------

    private boolean acquireQueued(boolean interruptible, long deadline) throws InterruptedException {
        Thread me = Thread.currentThread();
        waiters.add(me);
        boolean acquired = false;
        boolean interrupted = false;
        try {
            for (;;) {
                if (waiters.peek() == me && tryAcquire()) {
                    acquired = true;
                    return true;
                }
                if (Thread.interrupted()) {
                    if (interruptible) throw new InterruptedException();
                    interrupted = true;            // lock() odkłada przerwanie, zamiast na nie reagować
                }
                if (deadline == 0L) {
                    LockSupport.park(this);
                } else {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) return false;
                    LockSupport.parkNanos(this, remaining);
                }
            }
        } finally {
            waiters.remove(me);
            if (!acquired) unparkHead();           // mogliśmy trzymać sygnał przeznaczony dla nas
            if (interrupted) me.interrupt();       // ...a lock() oddaje przerwanie z powrotem
        }
    }

    // ---------- zwolnienie ----------

    @Override
    public void unlock() {
        if (OWNER.getVolatile(this) != Thread.currentThread()) {
            throw new IllegalMonitorStateException("this thread does not hold " + this);
        }
        int remaining = (int) STATE.get(this) - 1;     // zwykły zapis: nikt inny nie może go ruszać, dopóki blokada jest zajęta
        if (remaining > 0) {
            STATE.set(this, remaining);
            return;
        }
        OWNER.setVolatile(this, null);                 // wyczyść właściciela *przed* otwarciem bramy
        STATE.setVolatile(this, 0);                    // zwolnienie: publikuje sekcję krytyczną
        unparkHead();
    }

    private void unparkHead() {
        Thread head = waiters.peek();
        if (head != null) LockSupport.unpark(head);    // zezwolenie się zachowuje: bezpieczne, nawet jeśli wątek jeszcze nie zaparkował
    }

    // ---------- podgląd stanu, dla testów i toString ----------

    public boolean isLocked() {
        return (int) STATE.getVolatile(this) != 0;
    }

    public boolean isHeldByCurrentThread() {
        return OWNER.getVolatile(this) == Thread.currentThread();
    }

    public int getHoldCount() {
        return isHeldByCurrentThread() ? (int) STATE.get(this) : 0;
    }

    public boolean isFair() {
        return fair;
    }

    public boolean hasQueuedThreads() {
        return !waiters.isEmpty();
    }

    public boolean hasQueuedThread(Thread thread) {
        return waiters.contains(thread);
    }

    public int getQueueLength() {
        return waiters.size();
    }

    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("VarHandleLock has no conditions — use ReentrantLock");
    }

    @Override
    public String toString() {
        Thread holder = (Thread) OWNER.getVolatile(this);
        return "VarHandleLock[" + (fair ? "fair, " : "barging, ")
                + (holder == null ? "unlocked" : "held by " + holder.getName())
                + ", queued=" + waiters.size() + "]";
    }
}
