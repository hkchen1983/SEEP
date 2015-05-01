package uk.ac.imperial.lsds.streamsql.op.stateful;


public class IntMap {
	
	/* Note that the following value must be a power of two (see `hash`). */
	public static final int INTMAP_CONTENT_SIZE = 512;
	
	IntMapEntry [] content;
	
	int size = 0;
	
	int id = -1;
	
	long autoIndex = -1;
	
	public int size() {
		return this.size;
	}
	
	public IntMap (int id, long autoIndex) {
		content = new IntMapEntry[INTMAP_CONTENT_SIZE];
		for (int i = 0; i < content.length; i++)
			content[i] = null;
		this.id = id;
		this.autoIndex = autoIndex;
	}
	
	public IntMap () {
		this(-1, -1);
	}
	
	public int getId () {
		return id;
	}
	
	public void setId (int id) {
		this.id = id;
	}
	
	public long getAutoIndex () {
		return autoIndex;
	}

	public void put(int key, int value) {
		IntMapEntry current = content[hash(key)];
		
		if (current == null) {
			content[hash(key)] = IntMapEntryFactory.newInstance(id, key, value, null);
			size++;
		}
		else {
			while (current.key != key && current.next != null)
				current = current.next;
			
			if (current.key == key) {
				current.value = value;
			}
			else {
				current.next = IntMapEntryFactory.newInstance(id, key, value, null);
				size++;
			}
		}
	}
	
	public boolean containsKey(int key) {
		IntMapEntry current = content[hash(key)];
		
		if (current == null)
			return false;
		
		while (current.key != key && current.next != null)
			current = current.next;
		
		if (current.key == key)
			return true;
		
		return false;
	}

	public int get(int key) {
		IntMapEntry current = content[hash(key)];
		
		while (current.key != key && current.next != null)
			current = current.next;
		
		if (current.key != key)
			System.err.println("Error in IntMap");
		
		return current.value;
	}
	
	private int hash(int key) {
		return key & (INTMAP_CONTENT_SIZE-1);
	}
	
	public int clear() {
		size = 0;
		int count = 0;
		for (int i = 0; i < INTMAP_CONTENT_SIZE; i++) {
			if (content[i] != null) {
				IntMapEntry e = content[i];
				while (e != null) {
					IntMapEntry f = e.next;
					e.release(id); 
					count++;
					e = f;
				}
				content[i] = null;
			}
		}
		return count;
	}
	
	public void remove(int key) {
		IntMapEntry current = content[hash(key)];
		
		IntMapEntry previous = null;
		while (current.key != key && current.next != null) {
			previous = current;
			current = current.next;
		}
		
		if (current.key == key) {
			if (previous == null) {
				if (current.next != null) 
					content[hash(key)] = current.next;
				else 
					content[hash(key)] = null;
			}
			else {
				if (current.next != null) 
					previous.next = current.next;
				else
					previous.next = null;
			}
			current.release(id);
			size--;
		}
	}
	
	/*
	public int[] keySet() {
		int[] result = new int[size];
		
		int k = 0;
		for (int i = 0; i < INTMAP_CONTENT_SIZE; i++) {
			if (content[i] != null) {
				IntMapEntry e = content[i];
				result[k++] = e.key;
				while (e.next != null) {
					e = e.next;
					result[k++] = e.key;
				}
			}
		}
		return result;
	}
	*/

	public IntMapEntry [] getEntries() {
		return this.content;
	}
	
	public void release () {
		clear();
		IntMapFactory.free(this);
	}
	
	public String toString () {
		
		return String.format("[IntMap %03d pool-%02d %6d items] ", autoIndex, id, size);
	}
}
