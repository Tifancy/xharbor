/**
 * 
 */
package org.jocean.xharbor.util;

import java.net.URI;

import org.jocean.idiom.Emitter;
import org.jocean.idiom.InterfaceUtils;
import org.jocean.idiom.SimpleCache;
import org.jocean.idiom.Tuple;
import org.jocean.idiom.stats.TimeIntervalMemo;
import org.jocean.j2se.stats.BizMemoSupportMBean;
import org.jocean.j2se.stats.TIMemos;
import org.jocean.j2se.stats.TIMemos.EmitableTIMemo;
import org.jocean.xharbor.api.MarkableTarget;
import org.jocean.xharbor.api.RelayMemo;
import org.jocean.xharbor.api.RelayMemo.RESULT;
import org.jocean.xharbor.api.RelayMemo.STEP;
import org.jocean.xharbor.api.RoutingInfo;

import rx.functions.Action1;
import rx.functions.Action2;
import rx.functions.Func1;

/**
 * @author isdom
 *
 */
public class RelayMemoBuilderForStats implements RelayMemo.Builder {

    public RelayMemoBuilderForStats(final Action2<String, Emitter<String>> register) throws Exception {
        this._register = register;
        this._level0Memo = new RelayMemoImpl()
        .fillTimeIntervalMemoWith(new Func1<Enum<?>, TimeIntervalMemo>() {
            @Override
            public TimeIntervalMemo call(final Enum<?> e) {
                return _ttlMemos.get(Tuple.of(e));
            }});
        this._register.call("all", this._level0Memo);
    }
    
    @Override
    public RelayMemo build(final MarkableTarget target, final RoutingInfo info) {
        return InterfaceUtils.combineImpls(
            RelayMemo.class, 
            this._level0Memo,
            this._bizMemos.get(Tuple.of(normalizeString(info.getPath()))),
            this._bizMemos.get(Tuple.of(normalizeString(info.getPath()), info.getMethod())),
            this._bizMemos.get(Tuple.of(normalizeString(info.getPath()), info.getMethod(), 
                    uri2value(target.serviceUri())))
            );
    }

    private static final String[] _OBJNAME_KEYS = new String[]{"path", "method", "dest"};

    private static final String normalizeString(final String input) {
        return input.replaceAll(":", "-");
    }
    
    private static final String uri2value(final URI uri) {
        return normalizeString(uri.toString());
    }
    
    private static class RelayMemoImpl extends BizMemoSupportMBean<RelayMemoImpl, STEP, RESULT> 
        implements RelayMemo, Emitter<String> {
        
        public RelayMemoImpl() {
            super(STEP.class, RESULT.class);
        }
        
        @Override
        public void emit(final Action1<String> receptor) {
            for (STEP step : this._steps) {
                receptor.call("STEP:" + step.name() +":"+ this._stepCounters[step.ordinal()].get());
            }
            for (RESULT result : this._results) {
                receptor.call("RESULT:" + result.name() +":"+ this._resultCounters[result.ordinal()].get());
            }
        }
    }
    
    private final Action2<String, Emitter<String>> _register;
    
    private Func1<Tuple, EmitableTIMemo> _ttlMemoMaker = new Func1<Tuple, EmitableTIMemo>() {
        @Override
        public EmitableTIMemo call(final Tuple tuple) {
            return TIMemos.memo_10ms_30S();
        }};
        
    private Action2<Tuple, EmitableTIMemo> _ttlMemoRegister = new Action2<Tuple, EmitableTIMemo>() {
        @Override
        public void call(final Tuple tuple, final EmitableTIMemo newMemo) {
            final StringBuilder sb = new StringBuilder();
            Character splitter = null;
            //                      for last Enum<?>
            for ( int idx = 0; idx < tuple.size()-1; idx++) {
                if (null != splitter) {
                    sb.append(splitter);
                }
                sb.append(_OBJNAME_KEYS[idx]);
                sb.append("=");
                sb.append((String)tuple.getAt(idx));
                splitter = ',';
            }
            final Enum<?> stepOrResult = tuple.getAt(tuple.size()-1);
            final String category = stepOrResult.getClass().getSimpleName();
            final String ttl = stepOrResult.name();
            if (null != splitter) {
                sb.append(splitter);
            }
            sb.append("category=");
            sb.append(category);
            sb.append(',');
            sb.append("ttl=");
            sb.append(ttl);
            
            if ( null!=_register) {
                _register.call(sb.toString(), newMemo);
            }
        }};
            
    private SimpleCache<Tuple, EmitableTIMemo> _ttlMemos  = 
            new SimpleCache<Tuple, EmitableTIMemo>(this._ttlMemoMaker, this._ttlMemoRegister);
    
    private final Func1<Tuple, RelayMemoImpl> _bizMemoMaker = 
            new Func1<Tuple, RelayMemoImpl>() {
        @Override
        public RelayMemoImpl call(final Tuple tuple) {
            return new RelayMemoImpl()
                .fillTimeIntervalMemoWith(new Func1<Enum<?>, TimeIntervalMemo>() {
                    @Override
                    public TimeIntervalMemo call(final Enum<?> e) {
                        return _ttlMemos.get(tuple.append(e));
                    }});
        }};

    private final Action2<Tuple, RelayMemoImpl> _bizMemoRegister = 
            new Action2<Tuple, RelayMemoImpl>() {
        @Override
        public void call(final Tuple tuple, final RelayMemoImpl newMemo) {
            final StringBuilder sb = new StringBuilder();
            Character splitter = null;
            for ( int idx = 0; idx < tuple.size(); idx++) {
                if (null != splitter) {
                    sb.append(splitter);
                }
                sb.append(_OBJNAME_KEYS[idx]);
                sb.append("=");
                sb.append((String)tuple.getAt(idx));
                splitter = ',';
            }
            if ( null!=_register) {
                _register.call(sb.toString(), newMemo);
            }
        }};
        
    private final SimpleCache<Tuple, RelayMemoImpl> _bizMemos = 
            new SimpleCache<Tuple, RelayMemoImpl>(this._bizMemoMaker, this._bizMemoRegister);
    
    private final RelayMemoImpl _level0Memo;
}
