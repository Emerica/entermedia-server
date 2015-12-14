package org.entermediadb.asset.cluster;

import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openedit.Data;
import org.openedit.OpenEditException;
import org.openedit.data.Searcher;
import org.openedit.data.SearcherManager;
import org.openedit.hittracker.HitTracker;
import org.openedit.hittracker.SearchQuery;
import org.openedit.locks.Lock;
import org.openedit.locks.LockManager;

public class ClusterLockManager implements LockManager
{
	private static final Log log = LogFactory.getLog(ClusterLockManager.class);

	protected SearcherManager fieldSearcherManager;
	protected NodeManager fieldNodeManager;
	protected String fieldCatalogId;
	
	public String getCatalogId()
	{
		return fieldCatalogId;
	}

	public void setCatalogId(String inCatalogId)
	{
		fieldCatalogId = inCatalogId;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.entermediadb.locks.LockManagerI#lock(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public Lock lock(String inPath, String inOwnerId)
	{
		Searcher searcher = getLockSearcher();
		Lock lock = loadLock(inPath);
		
		//See if I already have the lock, because I created it or because I called this twice in a row
		
		int tries = 0;
		while (true)
		{
			Lock found = grabLock(lock,inOwnerId, inPath, searcher);
			if( found != null)
			{
				return found;
			}			
			tries++;
			if (tries > 9)
			{
				break;
			}
			try
			{
				Thread.sleep(250);
			}
			catch (Exception ex)
			{
				// does not happen
				log.info(ex);
			}
			log.info("Could not lock " + inPath + " trying again  " + tries);
			lock = loadLock(inPath);
		}
		throw new OpenEditException("Could not lock file " + inPath + " locked by " + lock.getNodeId() + " " + lock.getOwnerId());
	}
	public Lock grabLock(Lock lock, String inOwner, String inPath )
	{
		return grabLock(lock, inOwner, inPath,getLockSearcher());
	}
	public Lock grabLock(Lock lock, String inOwner, String inPath, Searcher inSearcher )
	{
		if( lock == null)
		{
			lock = createLock(inPath, inSearcher);
			lock.setNodeId(getNodeManager().getLocalNodeId());
			lock.setDate(new Date());
			lock.setLocked(true);
			lock.setOwnerId(inOwner);
			inSearcher.saveData(lock, null);
			
			String savedid = lock.getId();
			//See if anyone else also happen to save a lock and delete the older one
			SearchQuery q = inSearcher.createSearchQuery(); 
			q.addMatches("sourcepath", inPath);
			q.addSortBy("date"); 
			HitTracker tracker = inSearcher.search(q); //Make sure there was not a thread waiting
			tracker.setHitsPerPage(1);
			Iterator iter = tracker.iterator();
			Data first = (Data)iter.next();
			if (tracker.size() > 1) //Someone else also locked
			{
				//TODO: Delete the older one
				for (Iterator iterator = iter; iterator.hasNext();)
				{
					Data old = (Data) iterator.next();
					try
					{
						inSearcher.delete(old, null);
					}
					catch( Throwable ex)
					{
						log.error("Deleted already deleted lock");
					}
				}
			}
			
			if ( first.getId().equals(savedid))
			{
				return lock;
			}
			else
			{
				return null;
			}
		}

		if (lock.isLocked())
		{
			return null;
		}
		// set owner
		try
		{
			lock.setOwnerId(inOwner);
			lock.setDate(new Date());
			lock.setNodeId(getNodeManager().getLocalNodeId());
			lock.setLocked(true);
			getLockSearcher().saveData(lock, null);
		}
		catch (ConcurrentModificationException ex)
		{
			return null;
		}
		catch (OpenEditException ex)
		{
			if (ex.getCause() instanceof ConcurrentModificationException)
			{
				return null;
			}
			throw ex;
		}
		return lock;

	}

	protected Lock createLock(String inPath, Searcher searcher)
	{
		Lock lockrequest = (Lock) searcher.createNewData();
		lockrequest.setSourcePath(inPath);
		lockrequest.setLocked(false);
		return lockrequest;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.entermediadb.locks.LockManagerI#loadLock(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public Lock loadLock(String inPath)
	{
//		return loadLock(inPath, false, null);
//	}
//	public Lock loadLock(String inPath, boolean lockIt, String inOwner)
//	{
		Searcher searcher = getLockSearcher();

		SearchQuery q = searcher.createSearchQuery(); 
		q.addMatches("sourcepath", inPath);
		
		HitTracker tracker = searcher.search(q);
		tracker.setHitsPerPage(1);
		Data first = (Data) tracker.first();

		if (first == null)
		{
			return null;
		}
		//first = (Data) searcher.loadData(first); //This should already create a lock
		//kind of createNewData option
		Lock lock = new Lock();
		lock.setId(first.getId());
		lock.getProperties().putAll(first.getProperties());
		return lock;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.entermediadb.locks.LockManagerI#getLocksByDate(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public HitTracker getLocksByDate(String inPath)
	{
		Searcher searcher = getLockSearcher();
		SearchQuery q = searcher.createSearchQuery();
		q.addExact("sourcepath", inPath);
		q.addSortBy("date");
		return searcher.search(q);
	}

	public Searcher getLockSearcher()
	{
		Searcher searcher = getSearcherManager().getSearcher(getCatalogId(),"lock");
		return searcher;
	}

	public boolean isOwner(Lock lock)
	{
		if (lock == null)
		{
			throw new OpenEditException("Lock should not be null");
		}
		if (lock.getId() == null)
		{
			throw new OpenEditException("lock id is currently null");
		}

		Lock owner = loadLock(lock.getSourcePath());
		if (owner == null)
		{
			throw new OpenEditException("Owner lock is currently null");
		}
		if (lock.getOwnerId() == null)
		{
			return false;
		}
		boolean sameowner = lock.getOwnerId().equals(owner.getOwnerId());
		return sameowner;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.entermediadb.locks.LockManagerI#lockIfPossible(java.lang.String,
	 * java.lang.String, java.lang.String)
	 */
	@Override
	public Lock lockIfPossible(String inPath, String inOwnerId)
	{
		Lock lock = loadLock(inPath);

		if(lock != null && lock.isLocked())
		{
			return null;
		}
		lock = grabLock(lock, inOwnerId, inPath);
		return lock;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.entermediadb.locks.LockManagerI#release(java.lang.String,
	 * org.entermediadb.locks.Lock)
	 */
	@Override
	public boolean release(Lock inLock)
	{
		if (inLock != null)
		{
			Searcher searcher = getLockSearcher();
			inLock.setLocked(false);
			//inLock.setProperty("version", (String) null); //Once this is saved other people can go get it
			searcher.saveData(inLock, null);
			return true;
		}
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.entermediadb.locks.LockManagerI#releaseAll(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public void releaseAll(String inPath)
	{
		Lock existing = loadLock( inPath);
		release( existing);
	}

	public SearcherManager getSearcherManager()
	{
		return fieldSearcherManager;
	}

	public void setSearcherManager(SearcherManager inSearcherManager)
	{
		fieldSearcherManager = inSearcherManager;
	}

	public NodeManager getNodeManager()
	{
		return fieldNodeManager;
	}

	public void setNodeManager(NodeManager inNodeManager)
	{
		fieldNodeManager = inNodeManager;
	}
}
