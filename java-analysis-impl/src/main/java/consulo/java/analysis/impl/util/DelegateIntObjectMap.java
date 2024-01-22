package consulo.java.analysis.impl.util;

import consulo.util.collection.primitive.ints.IntObjConsumer;
import consulo.util.collection.primitive.ints.IntObjectMap;
import consulo.util.collection.primitive.ints.IntSet;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Set;

/**
 * @author VISTALL
 * @since 10/06/2021
 */
public class DelegateIntObjectMap<V> implements IntObjectMap<V>
{
	private IntObjectMap<V> myDelegate;

	public DelegateIntObjectMap(IntObjectMap<V> delegate)
	{
		myDelegate = delegate;
	}

	public IntObjectMap<V> getDelegate()
	{
		return myDelegate;
	}

	@Override
	@Nonnull
	public IntSet keySet()
	{
		return myDelegate.keySet();
	}

	@Override
	@Nonnull
	public int[] keys()
	{
		return myDelegate.keys();
	}

	@Override
	@Nullable
	public V put(int i, V v)
	{
		return myDelegate.put(i, v);
	}

	@Override
	@jakarta.annotation.Nullable
	public V get(int i)
	{
		return myDelegate.get(i);
	}

	@Override
	public boolean containsKey(int i)
	{
		return myDelegate.containsKey(i);
	}

	@Override
	public boolean containsValue(V v)
	{
		return myDelegate.containsValue(v);
	}

	@Override
	public V remove(int i)
	{
		return myDelegate.remove(i);
	}

	@Override
	@Nonnull
	public Set<IntObjectEntry<V>> entrySet()
	{
		return myDelegate.entrySet();
	}

	@Override
	@Nonnull
	public Collection<V> values()
	{
		return myDelegate.values();
	}

	@Override
	public int size()
	{
		return myDelegate.size();
	}

	@Override
	public boolean isEmpty()
	{
		return myDelegate.isEmpty();
	}

	@Override
	public void clear()
	{
		myDelegate.clear();
	}

	@Override
	public void forEach(IntObjConsumer<? super V> action)
	{
		myDelegate.forEach(action);
	}

	@Override
	public V putIfAbsent(int key, V value)
	{
		return myDelegate.putIfAbsent(key, value);
	}

	@Override
	public String toString()
	{
		return myDelegate.toString();
	}
}
