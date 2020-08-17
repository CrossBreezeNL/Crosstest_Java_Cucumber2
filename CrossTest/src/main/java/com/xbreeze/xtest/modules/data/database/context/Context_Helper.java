package com.xbreeze.xtest.modules.data.database.context;

import com.xbreeze.xtest.database.helpers.DataHelper;
import com.xbreeze.xtest.exception.XTestException;
import com.xbreeze.xtest.modules.data.database.Database_Helper;
import com.xbreeze.xtest.result.ResultContext;

public class Context_Helper extends Database_Helper {
	
	public Context_Helper(ResultContext resultContext, DataHelper dataHelper) throws XTestException {
		super(resultContext, dataHelper);
	}
	
	public void SpecifyTestTransaction() throws Throwable {
		this._resultContext.startTransaction();
	}
}