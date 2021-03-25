package edu.umass.ciir;

public class TasksRunnerException extends RuntimeException {
    public TasksRunnerException(String errorMessage) {
        super(errorMessage);
    }
    public TasksRunnerException(Exception cause) {
        super(cause);
    }
}
