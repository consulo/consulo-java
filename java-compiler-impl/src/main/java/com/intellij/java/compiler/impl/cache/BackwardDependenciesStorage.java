/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.compiler.impl.cache;

import consulo.compiler.CacheCorruptedException;
import consulo.util.collection.SLRUCache;
import consulo.index.io.data.DataExternalizer;
import consulo.index.io.EnumeratorIntegerDescriptor;
import consulo.index.io.PersistentHashMap;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.util.collection.primitive.ints.*;

import jakarta.annotation.Nonnull;
import java.io.*;
import java.util.*;

/**
 * @author Eugene Zhuravlev
 * Date: Dec 1, 2008
 */
public class BackwardDependenciesStorage implements Flushable, Disposable
{
	private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.CompilerDependencyStorage");
	protected final PersistentHashMap<Integer, DependenciesSet> myMap;
	protected final SLRUCache<Integer, ReferencerSetHolder> myCache;
	private Integer myKeyToRemove;
	private static final int FIELD = 1;
	private static final int METHOD = 2;
	private static final int CLASS = 3;

	public BackwardDependenciesStorage(File file, final int cacheSize) throws IOException
	{
		myMap = new PersistentHashMap<Integer, DependenciesSet>(file, EnumeratorIntegerDescriptor.INSTANCE, new MyDataExternalizer());

		myCache = new SLRUCache<Integer, ReferencerSetHolder>(cacheSize * 2, cacheSize)
		{
			@Nonnull
			public ReferencerSetHolder createValue(Integer key)
			{
				return new ReferencerSetHolder(key);
			}

			protected void onDropFromCache(Integer key, final ReferencerSetHolder holder)
			{
				if(key.equals(myKeyToRemove) || !holder.isDirty())
				{
					return;
				}
				try
				{
					if(holder.isDataLoaded() || !myMap.containsMapping(key))
					{
						myMap.put(key, new DependenciesSet(holder.getData()));
					}
					else
					{
						myMap.appendData(key, out -> {
							for(PrimitiveIterator.OfInt iterator = holder.myRemoveRequested.iterator();iterator.hasNext();)
							{
								int qName = iterator.nextInt();
								out.writeInt(-qName);
							}
							// process added members
							for(ReferencerItem item : holder.myAdded)
							{
								item.save(out);
							}
						});
					}
				}
				catch(IOException e)
				{
					LOG.error(e);
				}
			}
		};
	}

	public synchronized void remove(Integer qName) throws IOException
	{
		myKeyToRemove = qName;
		try
		{
			myCache.remove(qName);
		}
		finally
		{
			myKeyToRemove = null;
		}
		myMap.remove(qName);
	}

	public synchronized void removeReferencer(Integer qName, int referencerQName) throws IOException
	{
		if(myMap.containsMapping(qName))
		{
			final ReferencerSetHolder set = myCache.get(qName);
			set.removeReferencer(referencerQName);
		}
	}

	public synchronized void addClassReferencer(Integer qName, int referencerQName)
	{
		myCache.get(qName).addReferencer(new ReferencerItem(referencerQName));
	}

	public synchronized void addFieldReferencer(Integer qName, int referencerQName, int fieldName)
	{
		myCache.get(qName).addReferencer(new FieldReferencerItem(referencerQName, fieldName));
	}

	public synchronized void addMethodReferencer(Integer qName, int referencerQName, int methodName, int descriptor)
	{
		myCache.get(qName).addReferencer(new MethodReferencerItem(referencerQName, methodName, descriptor));
	}

	public synchronized Dependency[] getDependencies(Integer classQName) throws CacheCorruptedException
	{
		try
		{
			if(!myMap.containsMapping(classQName))
			{
				return Dependency.EMPTY_ARRAY;
			}

			return convertToDependencies(classQName.intValue(), myCache.get(classQName).getData());
		}
		catch(Throwable e)
		{
			throw new CacheCorruptedException(e);
		}
	}

	private static Dependency[] convertToDependencies(int classToSkip, Set<ReferencerItem> data)
	{
		final IntObjectMap<Dependency> dependencies = IntMaps.newIntObjectHashMap();
		for(ReferencerItem item : data)
		{
			if(item.qName == classToSkip)
			{
				continue; // skip self-dependencies
			}
			final Dependency dependency = addDependency(dependencies, item.qName);
			if(item instanceof FieldReferencerItem)
			{
				dependency.addField(((FieldReferencerItem) item).name);
			}
			else if(item instanceof MethodReferencerItem)
			{
				final MethodReferencerItem methodItem = (MethodReferencerItem) item;
				dependency.addMethod(methodItem.name, methodItem.descriptor);
			}
		}

		final Dependency[] dependencyArray = new Dependency[dependencies.size()];
		dependencies.forEach(new IntObjConsumer<>()
		{
			private int index = 0;

			public void accept(int key, Dependency object)
			{
				dependencyArray[index++] = object;
			}
		});
		return dependencyArray;
	}

	private static Dependency addDependency(IntObjectMap<Dependency> container, int classQName)
	{
		Dependency dependency = container.get(classQName);
		if(dependency == null)
		{
			dependency = new Dependency(classQName);
			container.put(classQName, dependency);
		}
		return dependency;
	}

	public synchronized void flush() throws IOException
	{
		myCache.clear();
		myMap.force();
	}

	private void flush(Integer key)
	{
		myCache.remove(key); // makes changes into PersistentHashMap
		myMap.force(); // flushes internal caches (which consume memory) and writes unsaved data to disk
	}

	public synchronized void dispose()
	{
		try
		{
			flush();
		}
		catch(IOException e)
		{
			LOG.info(e);
		}
		try
		{
			myMap.close();
		}
		catch(IOException e)
		{
			LOG.info(e);
		}
	}

	private static class ReferencerItem
	{
		public final int qName;

		private ReferencerItem(int qName)
		{
			this.qName = qName;
		}

		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}

			ReferencerItem that = (ReferencerItem) o;

			if(qName != that.qName)
			{
				return false;
			}

			return true;
		}

		public int hashCode()
		{
			return qName;
		}

		public void save(DataOutput out) throws IOException
		{
			out.writeInt(qName);
			out.writeByte(CLASS);
		}
	}

	private static final class MethodReferencerItem extends ReferencerItem
	{
		public final int name;
		public final int descriptor;

		MethodReferencerItem(int qName, int name, int descriptor)
		{
			super(qName);
			this.name = name;
			this.descriptor = descriptor;
		}

		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}
			if(!super.equals(o))
			{
				return false;
			}

			MethodReferencerItem that = (MethodReferencerItem) o;

			if(descriptor != that.descriptor)
			{
				return false;
			}
			if(name != that.name)
			{
				return false;
			}

			return true;
		}

		public int hashCode()
		{
			int result = super.hashCode();
			result = 31 * result + name;
			result = 31 * result + descriptor;
			return result;
		}

		public void save(DataOutput out) throws IOException
		{
			out.writeInt(qName);
			out.writeByte(METHOD);
			out.writeInt(name);
			out.writeInt(descriptor);
		}
	}

	private static final class FieldReferencerItem extends ReferencerItem
	{
		public final int name;

		FieldReferencerItem(int qName, int name)
		{
			super(qName);
			this.name = name;
		}

		public boolean equals(Object o)
		{
			if(this == o)
			{
				return true;
			}
			if(o == null || getClass() != o.getClass())
			{
				return false;
			}
			if(!super.equals(o))
			{
				return false;
			}

			FieldReferencerItem that = (FieldReferencerItem) o;

			if(name != that.name)
			{
				return false;
			}

			return true;
		}

		public int hashCode()
		{
			int result = super.hashCode();
			result = 31 * result + name;
			return result;
		}

		public void save(DataOutput out) throws IOException
		{
			out.writeInt(qName);
			out.writeByte(FIELD);
			out.writeInt(name);
		}
	}

	private class ReferencerSetHolder
	{
		private final Integer myKey;
		private IntSet myRemoveRequested = IntSets.newHashSet();
		private Set<ReferencerItem> myAdded = new HashSet<ReferencerItem>();

		private Set<ReferencerItem> myData = null;
		private boolean myIsDirty = false;

		public ReferencerSetHolder(Integer key)
		{
			myKey = key;
		}

		public void addReferencer(ReferencerItem referencer)
		{
			if(myData != null)
			{
				myIsDirty |= myData.add(referencer);
				return;
			}
			myAdded.add(referencer);
		}

		public void removeReferencer(int qName)
		{
			if(myData != null)
			{
				myIsDirty |= removeAllReferencerItems(myData, qName);
				return;
			}
			myRemoveRequested.add(qName);
			removeAllReferencerItems(myAdded, qName);
		}

		public boolean isDirty()
		{
			return myData != null ? myIsDirty : myRemoveRequested.size() > 0 || myAdded.size() > 0;
		}

		public boolean isDataLoaded()
		{
			return myData != null;
		}

		public Set<ReferencerItem> getData() throws IOException
		{
			if(myData == null)
			{
				final DependenciesSet ds = myMap.get(myKey);
				Set<ReferencerItem> set = null;
				if(ds != null)
				{
					set = ds.set;
					myIsDirty |= ds.needsCompacting;
				}
				if(set == null)
				{
					set = new HashSet<ReferencerItem>();
				}
				myIsDirty |= removeAllReferencerItems(set, myRemoveRequested);
				myIsDirty |= set.addAll(myAdded);
				myData = set;
				myAdded = null;
				myRemoveRequested = null;
			}
			return Collections.unmodifiableSet(myData);
		}

	}

	private static class MyDataExternalizer implements DataExternalizer<DependenciesSet>
	{
		public void save(DataOutput out, DependenciesSet ds) throws IOException
		{
			final IntSet classes = IntSets.newHashSet();
			final Map<Dependency.FieldRef, IntSet> fieldsMap = new HashMap<Dependency.FieldRef, IntSet>();
			final Map<Dependency.MethodRef, IntSet> methodsMap = new HashMap<Dependency.MethodRef, IntSet>();
			for(ReferencerItem item : ds.set)
			{
				if(item instanceof FieldReferencerItem)
				{
					final Dependency.FieldRef ref = new Dependency.FieldRef(((FieldReferencerItem) item).name);
					IntSet referencers = fieldsMap.get(ref);
					if(referencers == null)
					{
						referencers = IntSets.newHashSet();
						fieldsMap.put(ref, referencers);
					}
					referencers.add(item.qName);
				}
				else if(item instanceof MethodReferencerItem)
				{
					final MethodReferencerItem _item = (MethodReferencerItem) item;
					final Dependency.MethodRef ref = new Dependency.MethodRef(_item.name, _item.descriptor);
					IntSet referencers = methodsMap.get(ref);
					if(referencers == null)
					{
						referencers = IntSets.newHashSet();
						methodsMap.put(ref, referencers);
					}
					referencers.add(item.qName);
				}
				else
				{
					classes.add(item.qName);
				}
			}

			out.writeInt(classes.size());
			for(PrimitiveIterator.OfInt it = classes.iterator(); it.hasNext(); )
			{
				out.writeInt(it.nextInt());
			}

			out.writeInt(fieldsMap.size());
			for(Map.Entry<Dependency.FieldRef, IntSet> entry : fieldsMap.entrySet())
			{
				out.writeInt(entry.getKey().name);
				final IntSet referencers = entry.getValue();
				out.writeInt(referencers.size());
				for(PrimitiveIterator.OfInt rit = referencers.iterator(); rit.hasNext(); )
				{
					out.writeInt(rit.nextInt());
				}
			}

			out.writeInt(methodsMap.size());
			for(Map.Entry<Dependency.MethodRef, IntSet> entry : methodsMap.entrySet())
			{
				final Dependency.MethodRef ref = entry.getKey();
				out.writeInt(ref.name);
				out.writeInt(ref.descriptor);
				final IntSet referencers = entry.getValue();
				out.writeInt(referencers.size());
				for(PrimitiveIterator.OfInt rit = referencers.iterator(); rit.hasNext(); )
				{
					out.writeInt(rit.nextInt());
				}
			}
		}

		public DependenciesSet read(DataInput in) throws IOException
		{
			final Set<ReferencerItem> set = new HashSet<ReferencerItem>();

			int classesCount = in.readInt();
			while(classesCount-- > 0)
			{
				set.add(new ReferencerItem(in.readInt()));
			}

			int fieldsCount = in.readInt();
			while(fieldsCount-- > 0)
			{
				final int fieldName = in.readInt();
				int referencersCount = in.readInt();
				while(referencersCount-- > 0)
				{
					set.add(new FieldReferencerItem(in.readInt(), fieldName));
				}
			}

			int methodsCount = in.readInt();
			while(methodsCount-- > 0)
			{
				final int methodName = in.readInt();
				final int methodDescriptor = in.readInt();
				int referensersCount = in.readInt();
				while(referensersCount-- > 0)
				{
					set.add(new MethodReferencerItem(in.readInt(), methodName, methodDescriptor));
				}
			}
			boolean needsCompacting = false;
			// manage appends if exist qName, kind, {field | method}
			final DataInputStream _in = (DataInputStream) in;
			while(_in.available() > 0)
			{
				needsCompacting = true;
				final int qName = _in.readInt();
				if(qName < 0)
				{
					removeAllReferencerItems(set, -qName);
				}
				else
				{
					final byte kind = _in.readByte();
					if(kind == FIELD)
					{
						set.add(new FieldReferencerItem(qName, _in.readInt()));
					}
					else if(kind == METHOD)
					{
						final int name = _in.readInt();
						final int descriptor = _in.readInt();
						set.add(new MethodReferencerItem(qName, name, descriptor));
					}
					else if(kind == CLASS)
					{
						set.add(new ReferencerItem(qName));
					}
				}
			}

			return new DependenciesSet(set, needsCompacting);
		}
	}

	private static final class DependenciesSet
	{
		public final Set<ReferencerItem> set;
		public final boolean needsCompacting;

		public DependenciesSet(Set<ReferencerItem> set, boolean needsCompacting)
		{
			this.set = set;
			this.needsCompacting = needsCompacting;
		}

		public DependenciesSet(Set<ReferencerItem> set)
		{
			this(set, false);
		}
	}


	private static boolean removeAllReferencerItems(final Set<ReferencerItem> from, final IntSet qNames)
	{
		boolean removed = false;
		for(Iterator<ReferencerItem> it = from.iterator(); it.hasNext(); )
		{
			final ReferencerItem item = it.next();
			if(qNames.contains(item.qName))
			{
				it.remove();
				removed = true;
			}
		}
		return removed;
	}

	private static boolean removeAllReferencerItems(final Set<ReferencerItem> from, final int qNames)
	{
		boolean removed = false;
		for(Iterator<ReferencerItem> it = from.iterator(); it.hasNext(); )
		{
			final ReferencerItem item = it.next();
			if(qNames == item.qName)
			{
				it.remove();
				removed = true;
			}
		}
		return removed;
	}

}