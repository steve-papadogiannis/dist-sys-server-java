public interface Worker extends Runnable {

    void initialize();

    void waitForTasksThread();

}
