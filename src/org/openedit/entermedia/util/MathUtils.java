package org.openedit.entermedia.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MathUtils
{

	public BigDecimal getBigDecimal(String val)
	{
		if( val == null || val.contains(".") )
		{
			return new BigDecimal(0);
		}
		return new BigDecimal(val);
	}
	public float getPercentage(BigDecimal now, BigDecimal total)
	{
		if( total.doubleValue() > 0)
		{
			BigDecimal percentage=now.divide(total,2,RoundingMode.HALF_UP);
			return percentage.floatValue();
		}
		return 0;
		
	}
	public float getPercentage(int inSoFar, int inTotal)
	{
		BigDecimal now= new BigDecimal(inSoFar);
		BigDecimal total = new BigDecimal(inTotal);
		return getPercentage(now, total);
	}
	public float getPercentage(String inSoFar, String inTotal)
	{
		BigDecimal now=getBigDecimal(inSoFar);
		BigDecimal total = getBigDecimal(inTotal);
		return getPercentage(now, total);
	}

	
}
