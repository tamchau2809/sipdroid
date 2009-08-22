package org.sipdroid.sipua.ui;

/*
 * Copyright (C) 2009 The Sipdroid Open Source Project
 * 
 * This file is part of Sipdroid (http://www.sipdroid.org)
 * 
 * Sipdroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Contacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.text.TextUtils;
import android.util.Log;

public class Caller extends BroadcastReceiver {

		static long noexclude;
		
		@Override
		public void onReceive(Context context, Intent intent) {
	        String intentAction = intent.getAction();
	        String number = getResultData();
	        
	        if (intentAction.equals(Intent.ACTION_NEW_OUTGOING_CALL) && number != null)
	        {
        		if (!Sipdroid.release) Log.i("SipUA:","outgoing call");
    			boolean sip_type = PreferenceManager.getDefaultSharedPreferences(context).getString("pref","").equals("SIP");
    	        
				if (number.endsWith("+")) 
    			{
    				sip_type = !sip_type;
    				number = number.substring(0,number.length()-1);
    			}
				if (sip_type && SystemClock.elapsedRealtime() > noexclude + 10000) {
	    			String sExPat = PreferenceManager.getDefaultSharedPreferences(context).getString("excludepat", ""); 
	   				boolean bExNums = false;
					boolean bExTypes = false;
					if (sExPat.length() > 0) 
					{					
						Vector<String> vExPats = getTokens(sExPat, ",");
						Vector<String> vPatNums = new Vector<String>();
						Vector<Integer> vTypesCode = new Vector<Integer>();					
				    	for(int i = 0; i < vExPats.size(); i++)
			            {
				    		if (vExPats.get(i).startsWith("h") || vExPats.get(i).startsWith("H"))
			        			vTypesCode.add(Integer.valueOf(People.Phones.TYPE_HOME));
				    		else if (vExPats.get(i).startsWith("m") || vExPats.get(i).startsWith("M"))
			        			vTypesCode.add(Integer.valueOf(People.Phones.TYPE_MOBILE));
				    		else if (vExPats.get(i).startsWith("w") || vExPats.get(i).startsWith("W"))
			        			vTypesCode.add(Integer.valueOf(People.Phones.TYPE_WORK));
				    		else 
				    			vPatNums.add(vExPats.get(i));     
			            }
						if(vTypesCode.size() > 0)
							bExTypes = isExcludedType(vTypesCode, number, context);
						if(vPatNums.size() > 0)
							bExNums = isExcludedNum(vPatNums, number);   					
					}	
					if (bExTypes || bExNums)
						sip_type = false;
				}
				noexclude = 0;

    			if (!sip_type || !Sipdroid.on(context))
    			{
    				setResultData(number);
    			} 
    			else 
    			{
	        		if (number != null && !intent.getBooleanExtra("android.phone.extra.ALREADY_CALLED",false)) {
	        		    	// Migrate the "prefix" option. TODO Remove this code in a future release.
	        		    	SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
	        		    	if (sp.contains("prefix")) {
	        		    	    String prefix = sp.getString("prefix", "");
	        		    	    Editor editor = sp.edit();
	        		    	    if (!prefix.trim().equals("")) {
	        		    		editor.putString("search", "(.*)," + prefix + "\\1");
	        		    	    }
	        		    	    editor.remove("prefix");
	        		    	    editor.commit();
	        		    	}
	        		    	
	        		    	// Search & replace.
	    				String search = sp.getString("search", "");
	    				String callthru_number = number = searchReplaceNumber(search, number);
	    				String callthru_prefix;
	    				
						if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("par",false)) 
	    				{
	    					String orig = intent.getStringExtra("android.phone.extra.ORIGINAL_URI");	
	     					if (orig.lastIndexOf("/phones") >= 0) 
	    					{
	    						orig = orig.substring(0,orig.lastIndexOf("/phones")+7);
	        					Uri contactRef = Uri.parse(orig);
	        				    final String[] PHONES_PROJECTION = new String[] {
	         				        People.Phones.NUMBER, // 0
	        				        People.Phones.TYPE, // 1
	        				    };
	        			        Cursor phonesCursor = context.getContentResolver().query(contactRef, PHONES_PROJECTION, null, null,
	        			                Phones.ISPRIMARY + " DESC");
	        			        if (phonesCursor != null) 
	        			        {	        			        	
	        			        	number = "";
	        			            while (phonesCursor.moveToNext()) 
	        			            {
	        			                final int type = phonesCursor.getInt(1);
	        			                final String n = phonesCursor.getString(0);
	         			                if (TextUtils.isEmpty(n)) continue;
	         			                if (type == Phones.TYPE_MOBILE || type == Phones.TYPE_HOME || type == Phones.TYPE_WORK) 
	         			                {
	         			                	if (!number.equals("")) number = number + "&";
	         			                	number = number + searchReplaceNumber(search, n);
	        			                }
	        			            }
	        			            phonesCursor.close();
	        			        }
	        				}        					
	    				}
	    				if (Receiver.engine(context).call(number))
	    					setResultData(null);
	    				else if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean("callthru",false) &&
	    						(callthru_prefix = PreferenceManager.getDefaultSharedPreferences(context).getString("callthru2","")).length() > 0) {
	    					callthru_number = (callthru_prefix+","+callthru_number+"#").replaceAll(",", ",p");
	    					setResultData(callthru_number);
	    				}
	        		}
	            }
	        }
	    }
		
		private String searchReplaceNumber(String pattern, String number) {
		    // Comma should be safe as separator.
		    String[] split = pattern.split(",");
		    // We need exactly 2 parts: search and replace. Otherwise
		    // we just return the current number.
		    if (split.length != 2)
			return number;

		    String modNumber = split[1];
		    
		    try {
			// Compiles the regular expression. This could be done
			// when the user modify the pattern... TODO Optimize
			// this, only compile once.
			Pattern p = Pattern.compile(split[0]);
    		    	Matcher m = p.matcher(number);
    		    	// Main loop of the function.
    		    	if (m.matches()) {
    		    	    for (int i = 0; i < m.groupCount() + 1; i++) {
    		    		String r = m.group(i);
    		    		if (r != null) {
    		    		    modNumber = modNumber.replace("\\" + i, r);
    		    		}
    		    	    }
    		    	}
    		    	// If the modified number is the same as the replacement
    		    	// value, we guess that the user typed a bad replacement
    		    	// value and we use the original number.
    		    	if (modNumber.equals(split[1])) {
    		    	    modNumber = number;
    		    	}
		    } catch (PatternSyntaxException e) {
			// Wrong pattern syntax. Give back the original number.
			modNumber = number;
		    }
		    
		    // Returns the modified number.
		    return modNumber;
		}
	    
	    Vector<String> getTokens(String sInput, String sDelimiter)
	    {
	    	Vector<String> vTokens = new Vector<String>();				
			int iStartIndex = 0;				
			final int iEndIndex = sInput.lastIndexOf(sDelimiter);
			for (; iStartIndex < iEndIndex; iStartIndex++) 
			{
				int iNextIndex = sInput.indexOf(sDelimiter, iStartIndex);
				String sPattern = sInput.substring(iStartIndex, iNextIndex).trim();
				vTokens.add(sPattern);
				iStartIndex = iNextIndex; 
			}
			if(iStartIndex < sInput.length())
				vTokens.add(sInput.substring(iStartIndex, sInput.length()).trim());
		
			return vTokens;
	    }
	    
	    boolean isExcludedNum(Vector<String> vExNums, String sNumber)
	    {
			for (int i = 0; i < vExNums.size(); i++) 
			{
				Pattern p = null;
				Matcher m = null;
				try
				{					
					p = Pattern.compile(vExNums.get(i));
					m = p.matcher(sNumber);	
				}
				catch(PatternSyntaxException pse)
				{
		           return false;    
				}  
				if(m != null && m.find())
					return true;			
			}    		
			return false;
	    }
	    
	    boolean isExcludedType(Vector<Integer> vExTypesCode, String sNumber, Context oContext)
	    {
	    	Uri contactRef = Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, sNumber);
	    	final String[] PHONES_PROJECTION = new String[] 
		    {
		        People.Phones.NUMBER, // 0
		        People.Phones.TYPE, // 1
		    };
	        Cursor phonesCursor = oContext.getContentResolver().query(contactRef, PHONES_PROJECTION, null, null,
	                null);
			if (phonesCursor != null) 
	        {	        			
 	            while (phonesCursor.moveToNext()) 
	            { 			            	
	                final int type = phonesCursor.getInt(1);	              
	                if(vExTypesCode.contains(Integer.valueOf(type)))
	                	return true;	    
	            }
	            phonesCursor.close();
	        }
			return false;
	    }   
	    
}
