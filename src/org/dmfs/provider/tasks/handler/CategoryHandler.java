package org.dmfs.provider.tasks.handler;

import org.dmfs.provider.tasks.TaskContract.Categories;
import org.dmfs.provider.tasks.TaskContract.Properties;
import org.dmfs.provider.tasks.TaskContract.Property.Category;
import org.dmfs.provider.tasks.TaskContract.Tasks;
import org.dmfs.provider.tasks.TaskDatabaseHelper.CategoriesMapping;
import org.dmfs.provider.tasks.TaskDatabaseHelper.Tables;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;


/**
 * This is used to handle category property values
 * 
 * @author Tobias Reinsch <tobias@dmfs.org>
 * 
 */
public class CategoryHandler extends PropertyHandler
{

	private static final String[] CATEGORY_ID_PROJECTION = { Categories._ID, Categories.NAME, Categories.COLOR };

	private static final String CATEGORY_SELECTION = Categories._ID + "=? or " + Categories.NAME + "=?) and " + Categories.ACCOUNT_NAME + "=? and "
		+ Categories.ACCOUNT_TYPE + "=?";

	public static final String IS_NEW_CATEGORY = "is_new_category";


	@Override
	public ContentValues validateValues(SQLiteDatabase db, boolean isNew, ContentValues values, boolean isSyncAdapter)
	{
		// the category requires a name or an id
		if (!values.containsKey(Category.CATEGORY_ID) && !values.containsKey(Category.CATEGORY_NAME))
		{
			throw new IllegalArgumentException("neiter an id nor a category name was supplied");
		}

		// get the matching task & account for the property
		String[] queryArgs = { values.getAsString(Properties.TASK_ID) };
		String[] queryProjection = { Tasks.ACCOUNT_NAME, Tasks.ACCOUNT_TYPE };
		String querySelection = Tasks._ID + "=?";
		Cursor taskCursor = db.query(Tables.TASKS_VIEW, queryProjection, querySelection, queryArgs, null, null, null);

		String accountName = null;
		String accountType = null;
		try
		{
			{
				taskCursor.moveToNext();
				accountName = taskCursor.getString(0);
				accountType = taskCursor.getString(1);

				values.put(Categories.ACCOUNT_NAME, accountName);
				values.put(Categories.ACCOUNT_TYPE, accountType);

			}
		}
		finally
		{
			if (taskCursor != null)
			{
				taskCursor.close();
			}
		}

		if (accountName != null && accountType != null)
		{
			// search for matching categories
			String[] categoryArgs = { values.getAsString(Category.CATEGORY_ID), values.getAsString(Category.CATEGORY_NAME), accountName, accountType };
			Cursor cursor = db.query(Tables.CATEGORIES, CATEGORY_ID_PROJECTION, CATEGORY_SELECTION, categoryArgs, null, null, null);
			try
			{
				if (cursor != null && cursor.getCount() == 1)
				{
					cursor.moveToNext();
					Long categoryID = cursor.getLong(0);
					String categoryName = cursor.getString(1);
					int color = cursor.getInt(2);

					values.put(Category.CATEGORY_ID, categoryID);
					values.put(Category.CATEGORY_NAME, categoryName);
					values.put(Category.CATEGORY_COLOR, color);
					values.put(IS_NEW_CATEGORY, false);
				}
				else
				{
					values.put(IS_NEW_CATEGORY, true);
				}
			}
			finally
			{
				if (cursor != null)
				{
					cursor.close();
				}
			}

		}

		return values;
	}


	@Override
	public long insert(SQLiteDatabase db, ContentValues values, boolean isSyncAdapter)
	{
		values = validateValues(db, true, values, isSyncAdapter);
		values = getOrInsertCategory(db, values);
		insertRelation(db, values.getAsString(Category.TASK_ID), values.getAsString(Category.CATEGORY_ID));

		// insert property row and create relation
		return db.insert(Tables.PROPERTIES, "", values);
	}


	/**
	 * updating category properties is currently not supported
	 */
	@Override
	public int update(SQLiteDatabase db, ContentValues values, String selection, String[] selectionArgs, boolean isSyncAdapter)
	{
		super.update(db, values, selection, selectionArgs, isSyncAdapter);
		values = validateValues(db, true, values, isSyncAdapter);
		values = getOrInsertCategory(db, values);

		return db.update(Tables.PROPERTIES, values, selection, selectionArgs);
	}


	private ContentValues getOrInsertCategory(SQLiteDatabase db, ContentValues values)
	{
		if (values.getAsBoolean(IS_NEW_CATEGORY))
		{
			// insert new category in category table
			ContentValues newCategoryValues = new ContentValues();
			newCategoryValues.put(Categories.ACCOUNT_NAME, values.getAsString(Categories.ACCOUNT_NAME));
			newCategoryValues.put(Categories.ACCOUNT_TYPE, values.getAsString(Categories.ACCOUNT_TYPE));
			newCategoryValues.put(Categories.NAME, values.getAsString(Category.CATEGORY_NAME));
			newCategoryValues.put(Categories.COLOR, values.getAsInteger(Category.CATEGORY_COLOR));

			long categoryID = db.insert(Tables.CATEGORIES, "", newCategoryValues);
			values.put(Category.CATEGORY_ID, categoryID);
		}

		// remove redundant values
		values.remove(IS_NEW_CATEGORY);
		values.remove(Categories.ACCOUNT_NAME);
		values.remove(Categories.ACCOUNT_TYPE);

		return values;
	}


	private long insertRelation(SQLiteDatabase db, String taskId, String categoryId)
	{
		ContentValues relationValues = new ContentValues();
		relationValues.put(CategoriesMapping.TASK_ID, taskId);
		relationValues.put(CategoriesMapping.CATEGORY_ID, categoryId);
		return db.insert(Tables.CATEGORIES_MAPPING, "", relationValues);
	}
}
