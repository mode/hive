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
package org.apache.hadoop.hive.shims;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.AccessControlException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedExceptionAction;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.security.auth.login.LoginException;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.hive.shims.HadoopShims.StoragePolicyValue;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.mapred.ClusterStatus;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.JobProfile;
import org.apache.hadoop.mapred.JobStatus;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.mapred.lib.CombineFileSplit;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskID;
import org.apache.hadoop.security.Credentials;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.util.Progressable;

/**
 * In order to be compatible with multiple versions of Hadoop, all parts
 * of the Hadoop interface that are not cross-version compatible are
 * encapsulated in an implementation of this class. Users should use
 * the ShimLoader class as a factory to obtain an implementation of
 * HadoopShims corresponding to the version of Hadoop currently on the
 * classpath.
 */
public interface HadoopShims {

  /**
   * Constructs and Returns TaskAttempt Log Url
   * or null if the TaskLogServlet is not available
   *
   *  @return TaskAttempt Log Url
   */
  String getTaskAttemptLogUrl(JobConf conf,
      String taskTrackerHttpAddress,
      String taskAttemptId)
          throws MalformedURLException;

  /**
   * Returns a shim to wrap MiniMrCluster
   */
  public MiniMrShim getMiniMrCluster(Configuration conf, int numberOfTaskTrackers,
      String nameNode, int numDir) throws IOException;

  public MiniMrShim getMiniTezCluster(Configuration conf, int numberOfTaskTrackers,
      String nameNode, int numDir) throws IOException;

  public MiniMrShim getMiniSparkCluster(Configuration conf, int numberOfTaskTrackers,
      String nameNode, int numDir) throws IOException;

  /**
   * Shim for MiniMrCluster
   */
  public interface MiniMrShim {
    public int getJobTrackerPort() throws UnsupportedOperationException;
    public void shutdown() throws IOException;
    public void setupConfiguration(Configuration conf);
  }

  /**
   * Returns a shim to wrap MiniDFSCluster. This is necessary since this class
   * was moved from org.apache.hadoop.dfs to org.apache.hadoop.hdfs
   */
  MiniDFSShim getMiniDfs(Configuration conf,
      int numDataNodes,
      boolean format,
      String[] racks) throws IOException;

  /**
   * Shim around the functions in MiniDFSCluster that Hive uses.
   */
  public interface MiniDFSShim {
    FileSystem getFileSystem() throws IOException;

    void shutdown() throws IOException;
  }

  CombineFileInputFormatShim getCombineFileInputFormat();

  enum JobTrackerState { INITIALIZING, RUNNING };

  /**
   * Convert the ClusterStatus to its Thrift equivalent: JobTrackerState.
   * See MAPREDUCE-2455 for why this is a part of the shim.
   * @param clusterStatus
   * @return the matching JobTrackerState
   * @throws Exception if no equivalent JobTrackerState exists
   */
  public JobTrackerState getJobTrackerState(ClusterStatus clusterStatus) throws Exception;

  public TaskAttemptContext newTaskAttemptContext(Configuration conf, final Progressable progressable);

  public TaskAttemptID newTaskAttemptID(JobID jobId, boolean isMap, int taskId, int id);

  public JobContext newJobContext(Job job);

  /**
   * Check wether MR is configured to run in local-mode
   * @param conf
   * @return
   */
  public boolean isLocalMode(Configuration conf);

  /**
   * All retrieval of jobtracker/resource manager rpc address
   * in the configuration should be done through this shim
   * @param conf
   * @return
   */
  public String getJobLauncherRpcAddress(Configuration conf);

  /**
   * All updates to jobtracker/resource manager rpc address
   * in the configuration should be done through this shim
   * @param conf
   * @return
   */
  public void setJobLauncherRpcAddress(Configuration conf, String val);

  /**
   * All references to jobtracker/resource manager http address
   * in the configuration should be done through this shim
   * @param conf
   * @return
   */
  public String getJobLauncherHttpAddress(Configuration conf);

  /**
   * Move the directory/file to trash. In case of the symlinks or mount points, the file is
   * moved to the trashbin in the actual volume of the path p being deleted
   * @param fs
   * @param path
   * @param conf
   * @return false if the item is already in the trash or trash is disabled
   * @throws IOException
   */
  public boolean moveToAppropriateTrash(FileSystem fs, Path path, Configuration conf)
      throws IOException;

  /**
   * Get the default block size for the path. FileSystem alone is not sufficient to
   * determine the same, as in case of CSMT the underlying file system determines that.
   * @param fs
   * @param path
   * @return
   */
  public long getDefaultBlockSize(FileSystem fs, Path path);

  /**
   * Get the default replication for a path. In case of CSMT the given path will be used to
   * locate the actual filesystem.
   * @param fs
   * @param path
   * @return
   */
  public short getDefaultReplication(FileSystem fs, Path path);

  /**
   * Reset the default fair scheduler queue mapping to end user.
   *
   * @param conf
   * @param userName end user name
   */
  public void refreshDefaultQueue(Configuration conf, String userName)
      throws IOException;

  /**
   * The method sets to set the partition file has a different signature between
   * hadoop versions.
   * @param jobConf
   * @param partition
   */
  void setTotalOrderPartitionFile(JobConf jobConf, Path partition);

  Comparator<LongWritable> getLongComparator();

  /**
   * CombineFileInputFormatShim.
   *
   * @param <K>
   * @param <V>
   */
  interface CombineFileInputFormatShim<K, V> {
    Path[] getInputPathsShim(JobConf conf);

    void createPool(JobConf conf, PathFilter... filters);

    CombineFileSplit[] getSplits(JobConf job, int numSplits) throws IOException;

    CombineFileSplit getInputSplitShim() throws IOException;

    RecordReader getRecordReader(JobConf job, CombineFileSplit split, Reporter reporter,
        Class<RecordReader<K, V>> rrClass) throws IOException;
  }

  /**
   * Get the block locations for the given directory.
   * @param fs the file system
   * @param path the directory name to get the status and block locations
   * @param filter a filter that needs to accept the file (or null)
   * @return an list for the located file status objects
   * @throws IOException
   */
  List<FileStatus> listLocatedStatus(FileSystem fs, Path path,
                                     PathFilter filter) throws IOException;


  List<HdfsFileStatusWithId> listLocatedHdfsStatus(
      FileSystem fs, Path path, PathFilter filter) throws IOException;

  /**
   * For file status returned by listLocatedStatus, convert them into a list
   * of block locations.
   * @param fs the file system
   * @param status the file information
   * @return the block locations of the file
   * @throws IOException
   */
  BlockLocation[] getLocations(FileSystem fs,
      FileStatus status) throws IOException;

  /**
   * For the block locations returned by getLocations() convert them into a Treemap
   * <Offset,blockLocation> by iterating over the list of blockLocation.
   * Using TreeMap from offset to blockLocation, makes it O(logn) to get a particular
   * block based upon offset.
   * @param fs the file system
   * @param status the file information
   * @return TreeMap<Long, BlockLocation>
   * @throws IOException
   */
  TreeMap<Long, BlockLocation> getLocationsWithOffset(FileSystem fs,
      FileStatus status) throws IOException;

  /**
   * Flush and make visible to other users the changes to the given stream.
   * @param stream the stream to hflush.
   * @throws IOException
   */
  public void hflush(FSDataOutputStream stream) throws IOException;

  /**
   * For a given file, return a file status
   * @param conf
   * @param fs
   * @param file
   * @return
   * @throws IOException
   */
  public HdfsFileStatus getFullFileStatus(Configuration conf, FileSystem fs, Path file) throws IOException;

  /**
   * For a given file, set a given file status.
   * @param conf
   * @param sourceStatus
   * @param fs
   * @param target
   * @throws IOException
   */
  public void setFullFileStatus(Configuration conf, HdfsFileStatus sourceStatus,
    FileSystem fs, Path target) throws IOException;

  /**
   * Includes the vanilla FileStatus, and AclStatus if it applies to this version of hadoop.
   */
  public interface HdfsFileStatus {
    public FileStatus getFileStatus();
    public void debugLog();
  }

  public interface HdfsFileStatusWithId {
    public FileStatus getFileStatus();
    public Long getFileId();
  }

  public HCatHadoopShims getHCatShim();
  public interface HCatHadoopShims {

    enum PropertyName {CACHE_ARCHIVES, CACHE_FILES, CACHE_SYMLINK, CLASSPATH_ARCHIVES, CLASSPATH_FILES}

    public TaskID createTaskID();

    public TaskAttemptID createTaskAttemptID();

    public org.apache.hadoop.mapreduce.TaskAttemptContext createTaskAttemptContext(Configuration conf,
        TaskAttemptID taskId);

    public org.apache.hadoop.mapred.TaskAttemptContext createTaskAttemptContext(JobConf conf,
        org.apache.hadoop.mapred.TaskAttemptID taskId, Progressable progressable);

    public JobContext createJobContext(Configuration conf, JobID jobId);

    public org.apache.hadoop.mapred.JobContext createJobContext(JobConf conf, JobID jobId, Progressable progressable);

    public void commitJob(OutputFormat outputFormat, Job job) throws IOException;

    public void abortJob(OutputFormat outputFormat, Job job) throws IOException;

    /* Referring to job tracker in 0.20 and resource manager in 0.23 */
    public InetSocketAddress getResourceManagerAddress(Configuration conf);

    public String getPropertyName(PropertyName name);

    /**
     * Checks if file is in HDFS filesystem.
     *
     * @param fs
     * @param path
     * @return true if the file is in HDFS, false if the file is in other file systems.
     */
    public boolean isFileInHDFS(FileSystem fs, Path path) throws IOException;
  }
  /**
   * Provides a Hadoop JobTracker shim.
   * @param conf not {@code null}
   */
  public WebHCatJTShim getWebHCatShim(Configuration conf, UserGroupInformation ugi) throws IOException;
  public interface WebHCatJTShim {
    /**
     * Grab a handle to a job that is already known to the JobTracker.
     *
     * @return Profile of the job, or null if not found.
     */
    public JobProfile getJobProfile(org.apache.hadoop.mapred.JobID jobid) throws IOException;
    /**
     * Grab a handle to a job that is already known to the JobTracker.
     *
     * @return Status of the job, or null if not found.
     */
    public JobStatus getJobStatus(org.apache.hadoop.mapred.JobID jobid) throws IOException;
    /**
     * Kill a job.
     */
    public void killJob(org.apache.hadoop.mapred.JobID jobid) throws IOException;
    /**
     * Get all the jobs submitted.
     */
    public JobStatus[] getAllJobs() throws IOException;
    /**
     * Close the connection to the Job Tracker.
     */
    public void close();
    /**
     * Does exactly what org.apache.hadoop.mapreduce.Job#addCacheFile(URI) in Hadoop 2.
     * Assumes that both parameters are not {@code null}.
     */
    public void addCacheFile(URI uri, Job job);
    /**
     * Kills all jobs tagged with the given tag that have been started after the
     * given timestamp.
     */
    public void killJobs(String tag, long timestamp);
    /**
     * Returns all jobs tagged with the given tag that have been started after the
     * given timestamp. Returned jobIds are MapReduce JobIds.
     */
    public Set<String> getJobs(String tag, long timestamp);
  }

  /**
   * Create a proxy file system that can serve a given scheme/authority using some
   * other file system.
   */
  public FileSystem createProxyFileSystem(FileSystem fs, URI uri);

  public Map<String, String> getHadoopConfNames();
  
  /**
   * Create a shim for DFS storage policy.
   */
  
  public enum StoragePolicyValue {
    MEMORY, /* 1-replica memory */
    SSD, /* 3-replica ssd */
    DEFAULT /* system defaults (usually 3-replica disk) */;

    public static StoragePolicyValue lookup(String name) {
      if (name == null) {
        return DEFAULT;
      }
      return StoragePolicyValue.valueOf(name.toUpperCase().trim());
    }
  };
  
  public interface StoragePolicyShim {
    void setStoragePolicy(Path path, StoragePolicyValue policy) throws IOException;
  }
  
  /**
   *  obtain a storage policy shim associated with the filesystem.
   *  Returns null when the filesystem has no storage policies.
   */
  public StoragePolicyShim getStoragePolicyShim(FileSystem fs);

  /**
   * a hadoop.io ByteBufferPool shim.
   */
  public interface ByteBufferPoolShim {
    /**
     * Get a new ByteBuffer from the pool.  The pool can provide this from
     * removing a buffer from its internal cache, or by allocating a
     * new buffer.
     *
     * @param direct     Whether the buffer should be direct.
     * @param length     The minimum length the buffer will have.
     * @return           A new ByteBuffer. Its capacity can be less
     *                   than what was requested, but must be at
     *                   least 1 byte.
     */
    ByteBuffer getBuffer(boolean direct, int length);

    /**
     * Release a buffer back to the pool.
     * The pool may choose to put this buffer into its cache/free it.
     *
     * @param buffer    a direct bytebuffer
     */
    void putBuffer(ByteBuffer buffer);
  }

  /**
   * Provides an HDFS ZeroCopyReader shim.
   * @param in FSDataInputStream to read from (where the cached/mmap buffers are tied to)
   * @param in ByteBufferPoolShim to allocate fallback buffers with
   *
   * @return returns null if not supported
   */
  public ZeroCopyReaderShim getZeroCopyReader(FSDataInputStream in, ByteBufferPoolShim pool) throws IOException;

  public interface ZeroCopyReaderShim {
    /**
     * Get a ByteBuffer from the FSDataInputStream - this can be either a HeapByteBuffer or an MappedByteBuffer.
     * Also move the in stream by that amount. The data read can be small than maxLength.
     *
     * @return ByteBuffer read from the stream,
     */
    public ByteBuffer readBuffer(int maxLength, boolean verifyChecksums) throws IOException;
    /**
     * Release a ByteBuffer obtained from a read on the
     * Also move the in stream by that amount. The data read can be small than maxLength.
     *
     */
    public void releaseBuffer(ByteBuffer buffer);
  }

  public enum DirectCompressionType {
    NONE,
    ZLIB_NOHEADER,
    ZLIB,
    SNAPPY,
  };

  public interface DirectDecompressorShim {
    public void decompress(ByteBuffer src, ByteBuffer dst) throws IOException;
  }

  public DirectDecompressorShim getDirectDecompressor(DirectCompressionType codec);

  /**
   * Get configuration from JobContext
   */
  public Configuration getConfiguration(JobContext context);

  /**
   * Get job conf from the old style JobContext.
   * @param context job context
   * @return job conf
   */
  public JobConf getJobConf(org.apache.hadoop.mapred.JobContext context);

  public FileSystem getNonCachedFileSystem(URI uri, Configuration conf) throws IOException;

  public void getMergedCredentials(JobConf jobConf) throws IOException;

  public void mergeCredentials(JobConf dest, JobConf src) throws IOException;

  /**
   * Check if the configured UGI has access to the path for the given file system action.
   * Method will return successfully if action is permitted. AccessControlExceptoin will
   * be thrown if user does not have access to perform the action. Other exceptions may
   * be thrown for non-access related errors.
   * @param fs
   * @param status
   * @param action
   * @throws IOException
   * @throws AccessControlException
   * @throws Exception
   */
  public void checkFileAccess(FileSystem fs, FileStatus status, FsAction action)
      throws IOException, AccessControlException, Exception;

  /**
   * Use password API (if available) to fetch credentials/password
   * @param conf
   * @param name
   * @return
   */
  public String getPassword(Configuration conf, String name) throws IOException;

  /**
   * check whether current hadoop supports sticky bit
   * @return
   */
  boolean supportStickyBit();

  /**
   * Check stick bit in the permission
   * @param permission
   * @return sticky bit
   */
  boolean hasStickyBit(FsPermission permission);

  /**
   * @return True if the current hadoop supports trash feature.
   */
  boolean supportTrashFeature();

  /**
   * @return Path to HDFS trash, if current hadoop supports trash feature.  Null otherwise.
   */
  Path getCurrentTrashPath(Configuration conf, FileSystem fs);

  /**
   * Check whether file is directory.
   */
  boolean isDirectory(FileStatus fileStatus);

  /**
   * Returns a shim to wrap KerberosName
   */
  public KerberosNameShim getKerberosNameShim(String name) throws IOException;

  /**
   * Shim for KerberosName
   */
  public interface KerberosNameShim {
    public String getDefaultRealm();
    public String getServiceName();
    public String getHostName();
    public String getRealm();
    public String getShortName() throws IOException;
  }

  /**
   * Copies a source dir/file to a destination by orchestrating the copy between hdfs nodes.
   * This distributed process is meant to copy huge files that could take some time if a single
   * copy is done.
   *
   * @param src Path to the source file or directory to copy
   * @param dst Path to the destination file or directory
   * @param conf The hadoop configuration object
   * @return True if it is successfull; False otherwise.
   */
  public boolean runDistCp(Path src, Path dst, Configuration conf) throws IOException;

  /**
   * This interface encapsulates methods used to get encryption information from
   * HDFS paths.
   */
  public interface HdfsEncryptionShim {
    /**
     * Checks if a given HDFS path is encrypted.
     *
     * @param path Path to HDFS file system
     * @return True if it is encrypted; False otherwise.
     * @throws IOException If an error occurred attempting to get encryption information
     */
    public boolean isPathEncrypted(Path path) throws IOException;

    /**
     * Checks if two HDFS paths are on the same encrypted or unencrypted zone.
     *
     * @param path1 Path to HDFS file system
     * @param path2 Path to HDFS file system
     * @return True if both paths are in the same zone; False otherwise.
     * @throws IOException If an error occurred attempting to get encryption information
     */
    public boolean arePathsOnSameEncryptionZone(Path path1, Path path2) throws IOException;

    /**
     * Compares two encrypted path strengths.
     *
     * @param path1 HDFS path to compare.
     * @param path2 HDFS path to compare.
     * @return 1 if path1 is stronger; 0 if paths are equals; -1 if path1 is weaker.
     * @throws IOException If an error occurred attempting to get encryption/key metadata
     */
    public int comparePathKeyStrength(Path path1, Path path2) throws IOException;

    /**
     * create encryption zone by path and keyname
     * @param path HDFS path to create encryption zone
     * @param keyName keyname
     * @throws IOException
     */
    @VisibleForTesting
    public void createEncryptionZone(Path path, String keyName) throws IOException;

    /**
     * Creates an encryption key.
     *
     * @param keyName Name of the key
     * @param bitLength Key encryption length in bits (128 or 256).
     * @throws IOException If an error occurs while creating the encryption key
     * @throws NoSuchAlgorithmException If cipher algorithm is invalid.
     */
    @VisibleForTesting
    public void createKey(String keyName, int bitLength)
      throws IOException, NoSuchAlgorithmException;

    @VisibleForTesting
    public void deleteKey(String keyName) throws IOException;

    @VisibleForTesting
    public List<String> getKeys() throws IOException;
  }

  /**
   * This is a dummy class used when the hadoop version does not support hdfs encryption.
   */
  public static class NoopHdfsEncryptionShim implements HdfsEncryptionShim {
    @Override
    public boolean isPathEncrypted(Path path) throws IOException {
    /* not supported */
      return false;
    }

    @Override
    public boolean arePathsOnSameEncryptionZone(Path path1, Path path2) throws IOException {
    /* not supported */
      return true;
    }

    @Override
    public int comparePathKeyStrength(Path path1, Path path2) throws IOException {
    /* not supported */
      return 0;
    }

    @Override
    public void createEncryptionZone(Path path, String keyName) {
    /* not supported */
    }

    @Override
    public void createKey(String keyName, int bitLength) {
    /* not supported */
    }

    @Override
    public void deleteKey(String keyName) throws IOException {
    /* not supported */
    }

    @Override
    public List<String> getKeys() throws IOException{
    /* not supported */
      return null;
    }
  }

  /**
   * Returns a new instance of the HdfsEncryption shim.
   *
   * @param fs A FileSystem object to HDFS
   * @param conf A Configuration object
   * @return A new instance of the HdfsEncryption shim.
   * @throws IOException If an error occurred while creating the instance.
   */
  public HdfsEncryptionShim createHdfsEncryptionShim(FileSystem fs, Configuration conf) throws IOException;

  public Path getPathWithoutSchemeAndAuthority(Path path);

  /**
   * Reads data into ByteBuffer.
   * @param file File.
   * @param dest Buffer.
   * @return Number of bytes read, just like file.read. If any bytes were read, dest position
   *         will be set to old position + number of bytes read.
   */
  int readByteBuffer(FSDataInputStream file, ByteBuffer dest) throws IOException;

  /**
   * Get Delegation token and add it to Credential.
   * @param fs FileSystem object to HDFS
   * @param cred Credentials object to add the token to.
   * @param uname user name.
   * @throws IOException If an error occurred on adding the token.
   */
  public void addDelegationTokens(FileSystem fs, Credentials cred, String uname) throws IOException;

  /**
   * Gets file ID. Only supported on hadoop-2.
   * @return inode ID of the file.
   */
  long getFileId(FileSystem fs, String path) throws IOException;
}
