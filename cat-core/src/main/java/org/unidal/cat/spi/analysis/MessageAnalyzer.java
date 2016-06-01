package org.unidal.cat.spi.analysis;

import java.io.IOException;
import java.util.Map;

import com.dianping.cat.message.spi.MessageTree;
import org.unidal.helper.Threads.Task;

/**
 * Message analyzer is responsible of processing each message in the queue, and producing a report normally.
 * <p>
 *
 * For each hour, there is one instance will be instantiated and be assigned with a specific queue.
 */
public interface MessageAnalyzer extends Task {
	public boolean handle(MessageTree tree);

	public void configure(Map<String, String> properties);

	public void doCheckpoint(boolean atEnd) throws IOException;

	public String[] getDependencies();

	public void initialize(int index, int hour) throws IOException;

	public void destroy();
}
