/*******************************************************************************
 *
 *    Copyright (C) 2015-2016
 *  
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *  
 *      http://www.apache.org/licenses/LICENSE-2.0
 *  
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License. 
 *    
 *******************************************************************************/
package org.bboxdb.tools.gui;

import java.awt.event.ActionEvent;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;

import org.bboxdb.distribution.DistributionGroupName;
import org.bboxdb.distribution.mode.DistributionGroupZookeeperAdapter;
import org.bboxdb.distribution.zookeeper.ZookeeperClient;
import org.bboxdb.distribution.zookeeper.ZookeeperException;
import org.bboxdb.distribution.zookeeper.ZookeeperNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jgoodies.forms.builder.PanelBuilder;
import com.jgoodies.forms.layout.CellConstraints;
import com.jgoodies.forms.layout.FormLayout;

public class ConnectDialog {

	/**
	 * The default name of the cluster
	 */
	protected static final String DEFAULT_CLUSTER = "mycluster";
	
	/**
	 * The default name of the nodes
	 */
	protected static final String DEFAULT_NODE = "node1";

	/**
	 * The hosts field
	 */
	protected JTextField hosts;

	/**
	 * The clustername field
	 */
	protected JTextField clustername;

	/**
	 * The main frame
	 */
	protected JFrame mainframe;
	
	/**
	 * The logger
	 */
	protected final static Logger logger = LoggerFactory.getLogger(ConnectDialog.class);
	

	/**
	 * Show the connect dialog
	 */
	public void showDialog() {
		mainframe = new JFrame("BBoxDB - Connection Settings");
		hosts = new JTextField(DEFAULT_NODE);
		clustername = new JTextField(DEFAULT_CLUSTER);
		
		final PanelBuilder builder = buildDialog();
		
		mainframe.add(builder.getPanel());
		mainframe.pack();
		GuiHelper.setCenterPosition(mainframe);
		mainframe.setVisible(true);
	}

	/**
	 * Build the dialog
	 * @return
	 */
	protected PanelBuilder buildDialog() {
		// Close
		final Action closeAction = getCloseAction();
		final JButton closeButton = new JButton(closeAction);
		closeButton.setText("Close");
		
		// Connect
		final Action connectAction = getConnectAction();
		final JButton connectButton = new JButton(connectAction);
		connectButton.setText("Connect");

		final FormLayout layout = new FormLayout(
			    "right:pref, 3dlu, pref", 			// columns
			    "p, 3dlu, p, 3dlu, p, 9dlu, p");	// rows
		
		final PanelBuilder builder = new PanelBuilder(layout);
		builder.setDefaultDialogBorder();
		
		final CellConstraints cc = new CellConstraints();
		builder.addSeparator("Connection", cc.xyw(1,  1, 3));
		builder.addLabel("Zookeeper Hosts", cc.xy (1,  3));
		builder.add(hosts, cc.xy(3, 3));
		builder.addLabel("Clustername", cc.xy (1,  5));
		builder.add(clustername, cc.xy(3, 5));
		
		builder.add(connectButton, cc.xy(1, 7));
		builder.add(closeButton, cc.xy(3, 7));
		return builder;
	}

	/**
	 * Returns the close action
	 * @return
	 */
	protected AbstractAction getCloseAction() {
		return new AbstractAction() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = -1349800056154800682L;

			@Override
			public void actionPerformed(ActionEvent e) {
				System.exit(0);
			}
			
		};
	}

	/**
	 * Get the connect action
	 * @return
	 */
	protected Action getConnectAction() {
		final AbstractAction connectAction = new AbstractAction() {
			
			/**
			 * 
			 */
			private static final long serialVersionUID = 2908534701228350424L;

			@Override
			public void actionPerformed(ActionEvent e) {
				
				final List<String> zookeeperHosts = Arrays.asList(hosts.getText().split(","));
				final String cluster = clustername.getText();
				
				final ZookeeperClient zookeeperClient = new ZookeeperClient(zookeeperHosts, cluster);
				zookeeperClient.init();
				final DistributionGroupZookeeperAdapter distributionGroupZookeeperAdapter = new DistributionGroupZookeeperAdapter(zookeeperClient);

				if(! zookeeperClient.isConnected()) {
					JOptionPane.showMessageDialog(mainframe,
						    "Unable to connect to zookeeper.",
						    "Error",
						    JOptionPane.ERROR_MESSAGE);
					return;
				}
				
				try {
					mainframe.dispose();

					final List<DistributionGroupName> distributionGroups 
						= distributionGroupZookeeperAdapter.getDistributionGroups();

					if(distributionGroups.isEmpty()) {
						JOptionPane.showMessageDialog(mainframe,
							    "No distribution groups are found. Please create them first.",
							    "Distribution Groups",
							    JOptionPane.ERROR_MESSAGE);
						System.exit(0);
					}
					
					final ChooseDistributionGroupDialog chooseDistributionGroupDialog = 
							new ChooseDistributionGroupDialog(distributionGroups, zookeeperClient);
					
					chooseDistributionGroupDialog.showDialog();
					
				} catch (ZookeeperException | ZookeeperNotFoundException e1) {
					logger.error("Got an exception", e1);
				}
			}
			
		};
		return connectAction;
	}
}
