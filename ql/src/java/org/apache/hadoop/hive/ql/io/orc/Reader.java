/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hive.ql.io.orc;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;

/**
 * The interface for reading ORC files.
 *
 * One Reader can support multiple concurrent RecordReader.
 */
public interface Reader {

  /**
   * Get the number of rows in the file.
   * @return the number of rows
   */
  long getNumberOfRows();

  /**
   * Get the deserialized data size of the file
   * @return raw data size
   */
  long getRawDataSize();

  /**
   * Get the deserialized data size of the specified columns
   * @param colNames
   * @return raw data size of columns
   */
  long getRawDataSizeOfColumns(List<String> colNames);

  /**
   * Get the deserialized data size of the specified columns ids
   * @param colIds - internal column id (check orcfiledump for column ids)
   * @return raw data size of columns
   */
  long getRawDataSizeFromColIndices(List<Integer> colIds);

  /**
   * Get the user metadata keys.
   * @return the set of metadata keys
   */
  List<String> getMetadataKeys();

  /**
   * Get a user metadata value.
   * @param key a key given by the user
   * @return the bytes associated with the given key
   */
  ByteBuffer getMetadataValue(String key);

  /**
   * Did the user set the given metadata value.
   * @param key the key to check
   * @return true if the metadata value was set
   */
  boolean hasMetadataValue(String key);

  /**
   * Get the compression kind.
   * @return the kind of compression in the file
   */
  CompressionKind getCompression();

  /**
   * Get the buffer size for the compression.
   * @return number of bytes to buffer for the compression codec.
   */
  int getCompressionSize();

  /**
   * Get the number of rows per a entry in the row index.
   * @return the number of rows per an entry in the row index or 0 if there
   * is no row index.
   */
  int getRowIndexStride();

  /**
   * Get the list of stripes.
   * @return the information about the stripes in order
   */
  List<StripeInformation> getStripes();

  /**
   * Get the object inspector for looking at the objects.
   * @return an object inspector for each row returned
   */
  ObjectInspector getObjectInspector();

  /**
   * Get the length of the file.
   * @return the number of bytes in the file
   */
  long getContentLength();

  /**
   * Get the statistics about the columns in the file.
   * @return the information about the column
   */
  ColumnStatistics[] getStatistics();

  /**
   * Get the metadata information like stripe level column statistics etc.
   * @return the information about the column
   * @throws IOException
   */
  Metadata getMetadata() throws IOException;

  /**
   * Get the list of types contained in the file. The root type is the first
   * type in the list.
   * @return the list of flattened types
   */
  List<OrcProto.Type> getTypes();

  /**
   * Get the file format version.
   */
  OrcFile.Version getFileVersion();

  /**
   * Get the version of the writer of this file.
   */
  OrcFile.WriterVersion getWriterVersion();

  /**
   * Options for creating a RecordReader.
   */
  public static class Options {
    private boolean[] include;
    private long offset = 0;
    private long length = Long.MAX_VALUE;
    private SearchArgument sarg = null;
    private String[] columnNames = null;
    private Boolean useZeroCopy = null;
    private Boolean skipCorruptRecords = null;

    /**
     * Set the list of columns to read.
     * @param include a list of columns to read
     * @return this
     */
    public Options include(boolean[] include) {
      this.include = include;
      return this;
    }

    /**
     * Set the range of bytes to read
     * @param offset the starting byte offset
     * @param length the number of bytes to read
     * @return this
     */
    public Options range(long offset, long length) {
      this.offset = offset;
      this.length = length;
      return this;
    }

    /**
     * Set search argument for predicate push down.
     * @param sarg the search argument
     * @param columnNames the column names for
     * @return this
     */
    public Options searchArgument(SearchArgument sarg, String[] columnNames) {
      this.sarg = sarg;
      this.columnNames = columnNames;
      return this;
    }

    /**
     * Set whether to use zero copy from HDFS.
     * @param value the new zero copy flag
     * @return this
     */
    public Options useZeroCopy(boolean value) {
      this.useZeroCopy = value;
      return this;
    }

    /**
     * Set whether to skip corrupt records.
     * @param value the new skip corrupt records flag
     * @return this
     */
    public Options skipCorruptRecords(boolean value) {
      this.skipCorruptRecords = value;
      return this;
    }

    public boolean[] getInclude() {
      return include;
    }

    public long getOffset() {
      return offset;
    }

    public long getLength() {
      return length;
    }

    public SearchArgument getSearchArgument() {
      return sarg;
    }

    public String[] getColumnNames() {
      return columnNames;
    }

    public long getMaxOffset() {
      long result = offset + length;
      if (result < 0) {
        result = Long.MAX_VALUE;
      }
      return result;
    }

    public Boolean getUseZeroCopy() {
      return useZeroCopy;
    }

    public Boolean getSkipCorruptRecords() {
      return skipCorruptRecords;
    }

    public Options clone() {
      Options result = new Options();
      result.include = include;
      result.offset = offset;
      result.length = length;
      result.sarg = sarg;
      result.columnNames = columnNames;
      result.useZeroCopy = useZeroCopy;
      result.skipCorruptRecords = skipCorruptRecords;
      return result;
    }

    @Override
    public String toString() {
      StringBuilder buffer = new StringBuilder();
      buffer.append("{include: ");
      if (include == null) {
        buffer.append("null");
      } else {
        buffer.append("[");
        for(int i=0; i < include.length; ++i) {
          if (i != 0) {
            buffer.append(", ");
          }
          buffer.append(include[i]);
        }
        buffer.append("]");
      }
      buffer.append(", offset: ");
      buffer.append(offset);
      buffer.append(", length: ");
      buffer.append(length);
      if (sarg != null) {
        buffer.append(", sarg: ");
        buffer.append(sarg.toString());
        buffer.append(", columns: [");
        for(int i=0; i < columnNames.length; ++i) {
          if (i != 0) {
            buffer.append(", ");
          }
          buffer.append("'");
          buffer.append(columnNames[i]);
          buffer.append("'");
        }
        buffer.append("]");
      }
      buffer.append("}");
      return buffer.toString();
    }
  }

  /**
   * Create a RecordReader that reads everything with the default options.
   * @return a new RecordReader
   * @throws IOException
   */
  RecordReader rows() throws IOException;

  /**
   * Create a RecordReader that uses the options given.
   * This method can't be named rows, because many callers used rows(null)
   * before the rows() method was introduced.
   * @param options the options to read with
   * @return a new RecordReader
   * @throws IOException
   */
  RecordReader rowsOptions(Options options) throws IOException;

  /**
   * Create a RecordReader that will scan the entire file.
   * This is a legacy method and rowsOptions is preferred.
   * @param include true for each column that should be included
   * @return A new RecordReader
   * @throws IOException
   */
  RecordReader rows(boolean[] include) throws IOException;

  /**
   * Create a RecordReader that will start reading at the first stripe after
   * offset up to the stripe that starts at offset + length. This is intended
   * to work with MapReduce's FileInputFormat where divisions are picked
   * blindly, but they must cover all of the rows.
   * This is a legacy method and rowsOptions is preferred.
   * @param offset a byte offset in the file
   * @param length a number of bytes in the file
   * @param include true for each column that should be included
   * @return a new RecordReader that will read the specified rows.
   * @throws IOException
   */
  RecordReader rows(long offset, long length,
                    boolean[] include) throws IOException;

  /**
   * Create a RecordReader that will read a section of a file. It starts reading
   * at the first stripe after the offset and continues to the stripe that
   * starts at offset + length. It also accepts a list of columns to read and a
   * search argument.
   * This is a legacy method and rowsOptions is preferred.
   * @param offset the minimum offset of the first stripe to read
   * @param length the distance from offset of the first address to stop reading
   *               at
   * @param include true for each column that should be included
   * @param sarg a search argument that limits the rows that should be read.
   * @param neededColumns the names of the included columns
   * @return the record reader for the rows
   */
  RecordReader rows(long offset, long length,
                    boolean[] include, SearchArgument sarg,
                    String[] neededColumns) throws IOException;

  MetadataReader metadata() throws IOException;
}
