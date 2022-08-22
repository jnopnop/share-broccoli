package problems.heap;

public class MinHeap {
    private final int[] items;
    private final int maxSize;
    private int lastIndex;

    public MinHeap(int maxSize) {
        this.maxSize = maxSize + 1;
        this.items = new int[maxSize + 1];
        this.lastIndex = 1;
    }

    public int size() {
        return lastIndex - 1;
    }

    public int pop() {
        if (lastIndex < 2) {
            throw new IllegalStateException("Heap is empty");
        }
        int popped = items[1];
        lastIndex--;
        items[1] = items[lastIndex];
        int parent = 1;
        while (parent < (lastIndex / 2)) {
            int leftChild = parent * 2;
            int rightChild = parent * 2 + 1;
            if (items[parent] <= items[leftChild] && items[parent] <= items[rightChild]) {
                break;
            }

            int nextParent = items[leftChild] < items[rightChild]
                    ? leftChild
                    : rightChild;
            swap(parent, nextParent);
            parent = nextParent;
        }

        return popped;
    }

    private void swap(int left, int right) {
        int tmp = items[left];
        items[left] = items[right];
        items[right] = tmp;
    }

    public void add(int value) {
        if (lastIndex + 1 > maxSize) {
            throw new IllegalStateException("Out of capacity");
        }

        items[lastIndex] = value;
        int curr = lastIndex;
        while (curr > 1) {
            int parent = curr / 2;
            if (items[curr] > items[parent]) {
                break;
            }

            swap(curr, parent);
            curr = parent;
        }
        lastIndex++;
    }

    public static void main(String[] args) {
        MinHeap minHeap = new MinHeap(100);
        minHeap.add(4);
        minHeap.add(5);
        minHeap.add(1);
        minHeap.add(3);
        minHeap.add(2);
        minHeap.add(100);
        minHeap.add(-7);
        minHeap.add(78);
        minHeap.add(-45);
        minHeap.add(0);
        minHeap.add(1);

        while (minHeap.size() > 0) {
            System.out.println(minHeap.pop());
        }
    }
}
