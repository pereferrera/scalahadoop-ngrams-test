package com.datasalt.scala;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.WritableComparator;

/**
 * Base class that makes it easier to extend Text WritableComparators in Scala without
 * having to worry about overriding constructors.
 * 
 * @author pere
 *
 */
public class TextGroupingComparatorBase extends WritableComparator {

	protected TextGroupingComparatorBase() {
		super(Text.class, true);
  }
}
