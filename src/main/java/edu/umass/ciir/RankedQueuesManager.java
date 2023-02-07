package edu.umass.ciir;

import java.util.List;
import java.util.Queue;

public class RankedQueuesManager {
    private List<Queue<String>> queues;
    private int currentQ = -1;
    RankedQueuesManager(List<Queue<String>> queues) {
        this.queues = queues;
    }

    String getNextLine () {
        while (!allQueuesAreEmpty()) {
            bumpQ();
            if (queues.get(currentQ).size() > 0) {
                return queues.get(currentQ).remove();
            }
        }
        return null;
    }

    private boolean allQueuesAreEmpty() {
        for (Queue<String> q : queues) {
            if (q.size() > 0) {
                return false;
            }
        }
        return true;
    }

    private void bumpQ() {
        ++currentQ;
        if (currentQ == queues.size()) {
            currentQ = 0;
        }
    }

}
