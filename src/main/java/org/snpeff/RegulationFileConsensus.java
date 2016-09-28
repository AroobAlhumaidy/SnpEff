package org.snpeff;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.snpeff.fileIterator.RegulationFileIterator;
import org.snpeff.interval.Markers;
import org.snpeff.interval.Regulation;
import org.snpeff.util.Gpr;
import org.snpeff.util.Timer;

/**
 * Create a regulation consensus from a regulation file.
 *
 * @author pcingola
 */
public class RegulationFileConsensus {

	/**
	 * This class collapses adjacent intervals that appear
	 * consecutively within a regulatopn file
	 * @author pcingola
	 */
	class RegulationConsensus {
		int count = 1;
		Regulation consensus = null;

		void add(Regulation r) {
			if (consensus == null) {
				consensus = r;
				count = 1;
			} else {
				if (consensus.intersects(r)) {
					consensus.setStart(Math.max(consensus.getStart(), r.getStart()));
					consensus.setEnd(Math.max(consensus.getEnd(), r.getEnd()));
					count++;
				} else {
					flush();
					consensus = r;
					count = 1;
				}
			}
		}

		void flush() {
			if (consensus != null) {
				totalCount++;
				totalLength += consensus.size();

				List<Regulation> regs = getRegulationList(consensus.getRegulationType());
				regs.add(consensus);
			}
		}
	}

	boolean verbose = false;
	int totalCount = 0;
	long totalLength = 0;
	int totalLineNum = 0;

	HashMap<String, RegulationConsensus> regConsByName = new HashMap<String, RegulationFileConsensus.RegulationConsensus>();
	HashMap<String, ArrayList<Regulation>> regByRegType = new HashMap<String, ArrayList<Regulation>>();

	public RegulationFileConsensus(boolean verbose) {
		this.verbose = verbose;
	}

	/**
	 * Add to consensus
	 */
	public void consensus(Regulation reg) {
		String name = reg.getName();
		String cell = reg.getRegulationType();
		String key = cell + "_" + name;

		// Get or create
		RegulationConsensus regCons = regConsByName.get(key);
		if (regCons == null) {
			regCons = new RegulationConsensus();
			regConsByName.put(key, regCons);
		}

		regCons.add(reg);
	}

	public Collection<String> getRegTypes() {
		return regByRegType.keySet();
	}

	/**
	 * Get regulation list by cell type (or create a new list)
	 */
	public ArrayList<Regulation> getRegulationList(String regType) {
		ArrayList<Regulation> regs = regByRegType.get(regType);
		if (regs == null) {
			regs = new ArrayList<Regulation>();
			regByRegType.put(regType, regs);
		}
		return regs;
	}

	/**
	 * Read a file and add all regulation intervals
	 */
	public void readFile(RegulationFileIterator regulationFileIterator) {
		String chromo = "";
		int lineNum = 1;
		for (Regulation reg : regulationFileIterator) {

			// Different chromosome? flush all
			if (!chromo.equals(reg.getChromosomeName())) {
				for (RegulationConsensus regCons : regConsByName.values())
					regCons.flush();
				chromo = reg.getChromosomeName();
			}

			// Create consensus
			consensus(reg);

			// Show every now and then
			// if( lineNum % 100000 == 0 ) System.err.println("\t" + lineNum + " / " + totalCount + "\t" + String.format("%.1f%%", (100.0 * totalCount / lineNum)));
			lineNum++;
			totalLineNum++;
		}

		// Finished, flush all
		for (RegulationConsensus regCons : regConsByName.values())
			regCons.flush();

		// Show stats
		if (verbose) {
			Timer.showStdErr("Done");
			double perc = (100.0 * totalCount / totalLineNum);
			System.err.println("\tTotal lines                 : " + lineNum);
			System.err.println("\tTotal annotation count      : " + totalCount);
			System.err.println("\tPercent                     : " + String.format("%.1f%%", perc));
			System.err.println("\tTotal annotated length      : " + totalLength);
			System.err.println("\tNumber of cell/annotations  : " + regConsByName.size());
		}
	}

	/**
	 * Save databases (one file per cellType)
	 */
	public void save(String outputDir) {
		for (String regType : regByRegType.keySet()) {
			String rType = Gpr.sanityzeFileName(regType);
			String fileName = outputDir + "/regulation_" + rType + ".bin";
			if (verbose) Timer.showStdErr("Saving database '" + regType + "' in file '" + fileName + "'");

			// Save markers to file
			Markers markersToSave = new Markers();
			markersToSave.addAll(regByRegType.get(regType));
			markersToSave.save(fileName);
		}
	}

	void show(Regulation reg) {
		System.out.println(reg);
	}

}
