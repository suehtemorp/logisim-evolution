/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.hdlgenerator;

import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.file.FileWriter;
import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.LineBuffer;
import java.util.ArrayList;
import java.util.List;

public abstract class Hdl {

  public static final String NET_NAME = "s_LOGISIM_NET_";
  public static final String BUS_NAME = "s_LOGISIM_BUS_";

  public static boolean isVhdl() {
    return AppPreferences.HdlType.get().equals(HdlGeneratorFactory.VHDL);
  }

  public static boolean isVerilog() {
    return AppPreferences.HdlType.get().equals(HdlGeneratorFactory.VERILOG);
  }

  public static String bracketOpen() {
    return isVhdl() ? "(" : "[";
  }

  public static String bracketClose() {
    return isVhdl() ? ")" : "]";
  }

  public static int remarkOverhead() {
    return isVhdl() ? 3 : 4;
  }

  public static String getRemakrChar(boolean first, boolean last) {
    if (isVhdl()) return "-";
    if (first) return "/";
    if (last) return " ";
    return "*";
  }

  public static String getRemarkStart() {
    if (isVhdl()) return "-- ";
    return " ** ";
  }

  public static String assignPreamble() {
    return isVhdl() ? "" : "assign ";
  }

  public static String assignOperator() {
    return isVhdl() ? " <= " : " = ";
  }

  public static String notOperator() {
    return isVhdl() ? " NOT " : "~";
  }

  public static String andOperator() {
    return isVhdl() ? " AND " : "&";
  }

  public static String orOperator() {
    return isVhdl() ? " OR " : "|";
  }

  public static String xorOperator() {
    return isVhdl() ? " XOR " : "^";
  }

  public static String zeroBit() {
    return isVhdl() ? "'0'" : "1'b0";
  }

  public static String oneBit() {
    return isVhdl() ? "'1'" : "1'b1";
  }

  public static String unconnected(boolean empty) {
    return isVhdl() ? "OPEN" : empty ? "" : "'bz";
  }

  public static String vectorLoopId() {
    return isVhdl() ? " DOWNTO " : ":";
  }

  public static String getZeroVector(int nrOfBits, boolean floatingPinTiedToGround) {
    var contents = new StringBuilder();
    if (isVhdl()) {
      var fillValue = (floatingPinTiedToGround) ? "0" : "1";
      var hexFillValue = (floatingPinTiedToGround) ? "0" : "F";
      if (nrOfBits == 1) {
        contents.append("'").append(fillValue).append("'");
      } else {
        if ((nrOfBits % 4) > 0) {
          contents.append("\"");
          contents.append(fillValue.repeat((nrOfBits % 4)));
          contents.append("\"");
          if (nrOfBits > 3) {
            contents.append("&");
          }
        }
        if ((nrOfBits / 4) > 0) {
          contents.append("X\"");
          contents.append(hexFillValue.repeat(Math.max(0, (nrOfBits / 4))));
          contents.append("\"");
        }
      }
    } else {
      contents.append(nrOfBits).append("'d");
      contents.append(floatingPinTiedToGround ? "0" : "-1");
    }
    return contents.toString();
  }

  public static String getConstantVector(long value, int nrOfBits) {
    final var nrHexDigits = nrOfBits / 4;
    final var nrSingleBits = nrOfBits % 4;
    final var hexDigits = new String[nrHexDigits];
    final var singleBits = new StringBuffer();
    var shiftValue = value >> nrSingleBits;
    for (var hexIndex = nrHexDigits - 1; hexIndex >= 0; hexIndex--) {
      var hexValue = shiftValue & 0xFL;
      shiftValue >>= 4L;
      hexDigits[hexIndex] = String.format("%1X", hexValue);
    }
    final var hexValue = new StringBuffer();
    for (var hexIndex = 0; hexIndex < nrHexDigits; hexIndex++) {
      hexValue.append(hexDigits[hexIndex]);
    }
    var mask = (nrSingleBits == 0) ? 0 : 1L << (nrSingleBits - 1);
    while (mask > 0) {
      singleBits.append((value & mask) == 0 ? "0" : "1");
      mask >>= 1L;
    }
    // first case, we have to concatinate
    if ((nrHexDigits > 0) && (nrSingleBits > 0)) {
      if (Hdl.isVhdl()) {
        return LineBuffer.format("X\"{{1}}\"&\"{{2}}\"", hexValue.toString(), singleBits.toString());
      } else {
        return LineBuffer.format("{{{1}}'h{{2}}, {{3}}'b{{4}}}", nrHexDigits * 4, hexValue.toString(),
            nrSingleBits, singleBits.toString());
      }
    }
    // second case, we have only hex digits
    if (nrHexDigits > 0) {
      if (Hdl.isVhdl()) {
        return LineBuffer.format("X\"{{1}}\"", hexValue.toString());
      } else {
        return LineBuffer.format("{{1}}'h{{2}}", nrHexDigits * 4, hexValue.toString());
      }
    }
    // final case, we have only single bits
    if (Hdl.isVhdl()) {
      final var vhdlTicks = (nrOfBits == 1) ? "'" : "\"";
      return LineBuffer.format("{{1}}{{2}}{{1}}", vhdlTicks, singleBits.toString());
    }
    return LineBuffer.format("{{1}}'b{{2}}", nrSingleBits, singleBits.toString());
  }

  public static String getNetName(netlistComponent comp, int endIndex, boolean floatingNetTiedToGround, Netlist myNetlist) {
    var netName = "";
    if ((endIndex >= 0) && (endIndex < comp.nrOfEnds())) {
      final var floatingValue = floatingNetTiedToGround ? zeroBit() : oneBit();
      final var thisEnd = comp.getEnd(endIndex);
      final var isOutput = thisEnd.isOutputEnd();

      if (thisEnd.getNrOfBits() == 1) {
        final var solderPoint = thisEnd.get((byte) 0);
        if (solderPoint.getParentNet() == null) {
          // The net is not connected
          netName = LineBuffer.formatHdl(isOutput ? unconnected(true) : floatingValue);
        } else {
          // The net is connected, we have to find out if the connection
          // is to a bus or to a normal net.
          netName = (solderPoint.getParentNet().getBitWidth() == 1)
                  ? LineBuffer.formatHdl("{{1}}{{2}}", NET_NAME, myNetlist.getNetId(solderPoint.getParentNet()))
                  : LineBuffer.formatHdl("{{1}}{{2}}{{<}}{{3}}{{>}}", BUS_NAME,
                      myNetlist.getNetId(solderPoint.getParentNet()), solderPoint.getParentNetBitIndex());
        }
      }
    }
    return netName;
  }

  public static String getBusEntryName(netlistComponent comp, int endIndex, boolean floatingNetTiedToGround, int bitindex, Netlist theNets) {
    var busName = "";
    if ((endIndex >= 0) && (endIndex < comp.nrOfEnds())) {
      final var thisEnd = comp.getEnd(endIndex);
      final var isOutput = thisEnd.isOutputEnd();
      final var nrOfBits = thisEnd.getNrOfBits();
      if ((nrOfBits > 1) && (bitindex >= 0) && (bitindex < nrOfBits)) {
        if (thisEnd.get((byte) bitindex).getParentNet() == null) {
          // The net is not connected
          busName = LineBuffer.formatHdl(isOutput ? unconnected(false) : getZeroVector(1, floatingNetTiedToGround));
        } else {
          final var connectedNet = thisEnd.get((byte) bitindex).getParentNet();
          final var connectedNetBitIndex = thisEnd.get((byte) bitindex).getParentNetBitIndex();
          // The net is connected, we have to find out if the connection
          // is to a bus or to a normal net.
          busName =
              !connectedNet.isBus()
                  ? LineBuffer.formatHdl("{{1}}{{2}}", NET_NAME, theNets.getNetId(connectedNet))
                  : LineBuffer.formatHdl("{{1}}{{2}}{{<}}{{3}}{{>}}", BUS_NAME, theNets.getNetId(connectedNet), connectedNetBitIndex);
        }
      }
    }
    return busName;
  }

  public static String getBusNameContinues(netlistComponent comp, int endIndex, Netlist theNets) {
    if ((endIndex < 0) || (endIndex >= comp.nrOfEnds())) return null;
    final var connectionInformation = comp.getEnd(endIndex);
    final var nrOfBits = connectionInformation.getNrOfBits();
    if (nrOfBits == 1) return getNetName(comp, endIndex, true, theNets);
    if (!theNets.isContinuesBus(comp, endIndex)) return null;
    final var connectedNet = connectionInformation.get((byte) 0).getParentNet();
    return LineBuffer.format("{{1}}{{2}}{{<}}{{3}}{{4}}{{5}}{{>}}",
        BUS_NAME,
        theNets.getNetId(connectedNet),
        connectionInformation.get((byte) (connectionInformation.getNrOfBits() - 1)).getParentNetBitIndex(),
        Hdl.vectorLoopId(),
        connectionInformation.get((byte) (0)).getParentNetBitIndex());
  }

  public static String getBusName(netlistComponent comp, int endIndex, Netlist theNets) {
    if ((endIndex < 0) || (endIndex >= comp.nrOfEnds())) return null;
    final var connectionInformation = comp.getEnd(endIndex);
    final var nrOfBits = connectionInformation.getNrOfBits();
    if (nrOfBits == 1)  return getNetName(comp, endIndex, true, theNets);
    if (!theNets.isContinuesBus(comp, endIndex)) return null;
    final var ConnectedNet = connectionInformation.get((byte) 0).getParentNet();
    if (ConnectedNet.getBitWidth() != nrOfBits) return getBusNameContinues(comp, endIndex, theNets);
    return LineBuffer.format("{{1}}{{2}}", BUS_NAME, theNets.getNetId(ConnectedNet));
  }

  public static String getClockNetName(netlistComponent comp, int endIndex, Netlist theNets) {
    var contents = new StringBuilder();
    if ((theNets.getCurrentHierarchyLevel() != null) && (endIndex >= 0) && (endIndex < comp.nrOfEnds())) {
      final var endData = comp.getEnd(endIndex);
      if (endData.getNrOfBits() == 1) {
        final var ConnectedNet = endData.get((byte) 0).getParentNet();
        final var ConnectedNetBitIndex = endData.get((byte) 0).getParentNetBitIndex();
        /* Here we search for a clock net Match */
        final var clocksourceid = theNets.getClockSourceId(
            theNets.getCurrentHierarchyLevel(), ConnectedNet, ConnectedNetBitIndex);
        if (clocksourceid >= 0) {
          contents.append(HdlGeneratorFactory.CLOCK_TREE_NAME).append(clocksourceid);
        }
      }
    }
    return contents.toString();
  }

  public static boolean writeEntity(String targetDirectory, List<String> contents, String componentName) {
    if (!Hdl.isVhdl()) return true;
    if (contents.isEmpty()) {
      // FIXME: hardcoded string
      Reporter.report.addFatalError("INTERNAL ERROR: Empty entity description received!");
      return false;
    }
    final var outFile = FileWriter.getFilePointer(targetDirectory, componentName, true);
    if (outFile == null) return false;
    return FileWriter.writeContents(outFile, contents);
  }

  public static boolean writeArchitecture(String targetDirectory, List<String> contents, String componentName) {
    if (contents == null || contents.isEmpty()) {
      // FIXME: hardcoded string
      Reporter.report.addFatalError(
          "INTERNAL ERROR: Empty behavior description for Component '"
              + componentName
              + "' received!");
      return false;
    }
    final var outFile = FileWriter.getFilePointer(targetDirectory, componentName, false);
    if (outFile == null)  return false;
    return FileWriter.writeContents(outFile, contents);
  }

}
