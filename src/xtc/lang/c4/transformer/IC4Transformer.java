package xtc.lang.c4.transformer;

import java.util.List;

import xtc.tree.GNode;

/**
 * An interface for transformers.
 * 
 * @author Marco Yuen
 * @version $Revision: 1.1 $
 */
public interface IC4Transformer {
  /**
   * Transform the advice to C.
   * 
   * @return A list of transformed node.
   */
  public List<GNode> transform();
}
