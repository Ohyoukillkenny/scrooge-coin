import java.security.PublicKey;
import java.util.*;

public class TxHandler {
    private UTXOPool myUTXOPool;
    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. This should make a copy of utxoPool by using the UTXOPool(UTXOPool uPool)
     * constructor.
     */
    public TxHandler(UTXOPool utxoPool) {
        // IMPLEMENT THIS
        this.myUTXOPool = new UTXOPool(utxoPool);
    }

    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */
    public boolean isValidTx(Transaction tx) {
        HashSet<UTXO> knownUTXO = new HashSet<UTXO>();
        int index = 0;
        double V_In = 0.0;
        double V_out = 0.0;
        for(Transaction.Input inp : tx.getInputs()){
            /** (1) all outputs in UTXO pool means that all pretxHash and index in inputs of tx
             * can be found in myUTXOpool */
            // now the ut is the entry of one previous output
            UTXO ut = new UTXO(inp.prevTxHash, inp.outputIndex);
            if (!this.myUTXOPool.contains(ut)){
                return false;
            }
            /** (2) ut.getTxOutput find the prev tx output, whose sig indicates the current
             * owner of the coin, which should be similar with sig in current inputs*/
            PublicKey prevSig = this.myUTXOPool.getTxOutput(ut).address;
            Crypto myCript = new Crypto();
            if(!myCript.verifySignature(prevSig, tx.getRawDataToSign(index), inp.signature)){
                return false;
            }
            /** (3) double claimed by tx means in tx, there are >= 2 inputs
             * calling same output*/
            if (knownUTXO.contains(ut)){
                return false;
            }
            knownUTXO.add(ut);

            double V_prevOut = this.myUTXOPool.getTxOutput(ut).value;
            V_In += V_prevOut;

            index ++;
        }
        for(Transaction.Output oup : tx.getOutputs()){
            /** (4) */
            if(oup.value < 0.0){
                return false;
            }
            V_out += oup.value;
        }
        /** (5) */
        return V_out <= V_In;
    }

    private void updatePool(Transaction tx){
        // remove old uts in myUTXOPool
        for (Transaction.Input inp : tx.getInputs()){
            UTXO ut = new UTXO(inp.prevTxHash, inp.outputIndex);
            this.myUTXOPool.removeUTXO(ut);
        }
        // add new uts in myUTXOPool
        byte[] txHash = tx.getHash();
        int index = 0;
        for (Transaction.Output oup : tx.getOutputs()){
            UTXO ut = new UTXO(txHash, index);
            index ++;
            this.myUTXOPool.addUTXO(ut,oup);
        }
    }
    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        /** (1) Return mutually valid array (unordered) of accepted transactions
         *  (2) Updating the current UTXO pool */
        HashSet<Transaction> myTxs = new HashSet<Transaction>(Arrays.asList(possibleTxs));
        int cnt = 0;
        ArrayList<Transaction> validTxs = new ArrayList<Transaction>();
        /** we have valify the transactions by while loop since the txs are unordered,
         *  and causality might exist between different txs */
        do{
            cnt = myTxs.size();
            HashSet<Transaction> removeTxs = new HashSet<Transaction>();
            for (Transaction tx : myTxs) {
                if(!isValidTx(tx)){
                    continue;
                }
                validTxs.add(tx);
                updatePool(tx);
                removeTxs.add(tx);
            }
            for(Transaction tx : removeTxs){
                myTxs.remove(tx);
            }
        } while (cnt != myTxs.size() && cnt != 0);
        return validTxs.toArray(new Transaction[validTxs.size()]);
    }
}
