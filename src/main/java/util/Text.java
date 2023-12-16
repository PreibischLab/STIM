package util;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public class Text
{
	public static void copyToClipboard( final String text )
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents( new StringSelection(text), null );
	}
}
