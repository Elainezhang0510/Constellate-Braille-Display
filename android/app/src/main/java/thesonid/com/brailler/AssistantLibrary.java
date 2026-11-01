package thesonid.com.brailler;

import android.app.Activity;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import androidx.core.widget.TextViewCompat;
import java.util.*;

/** Native Assistant-only word books. The shared navigation and other pages stay in the activity. */
final class AssistantLibrary {
  interface Host {
    void speak(String text);
    void stopSpeech();
    void hideKeyboard();
    void send(String word);
  }
  private final Activity activity;
  private final Host host;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private ConstellateUi ui;
  private ConstellateWords library;
  private WordBooks books;
  private String scope = "", bookId = "", word = "", draft = "";
  private LinearLayout root, rows;
  private EditText input;
  private String state = "shelf", searchTerm = "";
  private int guideStage = -1;
  private boolean active, pending;
  private BottomSheetDialog nameDialog;
  private androidx.appcompat.app.AlertDialog deleteDialog;
  private final Set<String> deleting = new HashSet<>();

  AssistantLibrary(Activity activity, Host host) { this.activity=activity;this.host=host; }
  void bind(LinearLayout root, ConstellateUi ui, ConstellateWords library, String uid) {
    String board=SessionStore.getBoardId(activity), nextScope=uid+":"+board;
    if (!nextScope.equals(scope)) {
      stop();scope=nextScope;books=new WordBooks(activity,uid,board);bookId="";state="shelf";searchTerm="";draft="";pending=false;deleting.clear();
    }
    if (books==null) books=new WordBooks(activity,uid,board);
    if (this.library!=library) { pending=false;deleting.clear(); }
    this.root=root;this.ui=ui;this.library=library;active=true;
    if(library!=null) books.migrate(library.words,library.loaded);
    if (!bookId.isEmpty() && books.find(bookId)==null) {bookId="";state="shelf";}
    render();
  }
  void changed() {
    if (!active || library==null) return;
    books.migrate(library.words,library.loaded);
    if (guideStage>=0 || pending) return;
    if(state.equals("shelf")||state.equals("search")) render();
    else if(state.equals("book")) updateRows();
  }
  void stop() {
    active=false;handler.removeCallbacksAndMessages(null);
    if(deleteDialog!=null){deleteDialog.dismiss();deleteDialog=null;}
    if(nameDialog!=null){nameDialog.dismiss();nameDialog=null;}
    if(guideStage>=0){guideStage=-1;state="shelf";host.stopSpeech();}
  }
  boolean back() {
    if(guideStage>=0){guideStage=-1;handler.removeCallbacksAndMessages(null);host.stopSpeech();state="shelf";render();return true;}
    if(state.equals("search")){state="shelf";searchTerm="";render();return true;}
    if(state.equals("braille")){state=searchTerm.isEmpty()?"book":"search";render();return true;}
    if(state.equals("book")){host.hideKeyboard();state="shelf";draft="";render();return true;}
    return false;
  }
  void save(Bundle out) {
    out.putString("wordbook_search",searchTerm);out.putString("wordbook_scope",scope);out.putString("wordbook_id",bookId);
    out.putString("wordbook_state",guideStage>=0?"shelf":state);out.putString("wordbook_word",word);out.putString("wordbook_draft",draft);
  }
  void restore(Bundle in) {
    if(in==null)return;searchTerm=in.getString("wordbook_search","");scope=in.getString("wordbook_scope","");bookId=in.getString("wordbook_id","");
    state=in.getString("wordbook_state","shelf");word=in.getString("wordbook_word","");draft=in.getString("wordbook_draft","");
    // Preferences are loaded on bind, while retaining the restored location.
  }
  private void toast(String text) { Toast.makeText(activity,text,Toast.LENGTH_SHORT).show(); }
  private WordBooks.Book current() { return books.find(bookId); }
  private void render() {
    if(!active)return;
    if(root.getParent() instanceof ScrollView)((ScrollView)root.getParent()).scrollTo(0,0);
    root.removeAllViews();rows=null;input=null;
    if(guideStage>=0){renderGuide();return;}
    if(state.equals("search")){searchResults();}
    else if(state.equals("book") && current()!=null){title(current().name,true);addBar(false);rows=ui.column();ui.add(root,rows,24);updateRows();}
    else if(state.equals("braille") && !word.isEmpty()){title("Braille display",true);braille(word);}
    else {state="shelf";title("Word Library",false);shelves(false);}
  }
  void search(String query){
    host.hideKeyboard();guideStage=-1;handler.removeCallbacksAndMessages(null);host.stopSpeech();
    searchTerm=ConstellateRules.word(query).trim();state="search";render();
  }
  void includeVoiceWord(String value) {
    WordBooks.Book book = current();
    if (book == null) {
      for (WordBooks.Book candidate : books.books)
        if (candidate.name.equals("Everyday")) { book = candidate; break; }
    }
    if (book == null) book = books.create("Everyday");
    books.add(book, value);
    if (active) render();
  }
  private void searchResults(){
    title("Search results",true);ui.body(root,"Results for “"+searchTerm+"”");
    int count=0;
    if(library!=null)for(String value:library.words){
      if(!value.contains(searchTerm))continue;
      SwipeWordRow wrapper=wordRow(value,false);
      View card=wrapper.getChildAt(1);wrapper.removeView(card);
      // Results keep voice / send / preview actions; removal stays inside the book.
      card.setAccessibilityDelegate(null);ui.add(root,card,16);count++;
    }
    if(count==0)ui.body(root,library!=null&&!library.loaded?"Your words are still loading…":"No matching words. Try another word.");
  }
  private void title(String value, boolean back) {
    LinearLayout row=ui.row();
    if(back){ImageButton button=icon(R.drawable.ic_constellate_arrow,"Back",()->back());button.setBackgroundColor(Color.TRANSPARENT);button.setRotation(180);row.addView(button,new LinearLayout.LayoutParams(ui.dp(36),ui.dp(48)));}
    TextView t=ui.text(value,30,true);androidx.core.view.ViewCompat.setAccessibilityHeading(t,true);
    row.addView(t,new LinearLayout.LayoutParams(0,-2,1));ui.add(root,row,0);ui.gap(root,28);
  }
  private void shelves(boolean demonstration) {
    List<View> tiles=new ArrayList<>();
    tiles.add(cover("Starter\nguide",0,true,()->startGuide()));
    if(!demonstration)for(WordBooks.Book book:books.books) {
      View tile=cover(book.name,book.words.size(),false,()->{bookId=book.id;state="book";draft="";render();});
      tile.setOnLongClickListener(v->{confirmDeleteBook(book);return true;});
      tiles.add(tile);
    }
    tiles.add(newBook(demonstration?()->{}:this::nameBook));
    for(int i=0;i<tiles.size();i+=2){
      LinearLayout row=ui.row();ui.padding(row,18,0);
      LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,ui.dp(194),1);row.addView(tiles.get(i),left);
      Space gap=new Space(activity);row.addView(gap,new LinearLayout.LayoutParams(ui.dp(36),1));
      if(i+1<tiles.size()) row.addView(tiles.get(i+1),new LinearLayout.LayoutParams(0,ui.dp(194),1));
      else row.addView(new Space(activity),new LinearLayout.LayoutParams(0,1,1));
      ui.add(root,row,28);shelf();ui.gap(root,28);
    }
    ui.gap(root,142);shelf();
  }
  private void shelf(){View v=new View(activity);v.setBackground(ui.shape(ui.surface,ui.surface,3,0));v.setElevation(ui.dp(5));ui.add(root,v,6);v.getLayoutParams().height=ui.dp(10);}
  private View cover(String name,int count,boolean guide,Runnable click){
    FrameLayout frame=new FrameLayout(activity);frame.setContentDescription(guide?"Starter guide":name+", "+count+" words");frame.setFocusable(true);frame.setOnClickListener(v->click.run());
    View art=new View(activity){final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);@Override protected void onDraw(Canvas c){
      float w=getWidth(),h=getHeight();paint.setShader(null);paint.setColor(ui.surface);c.drawRoundRect(ui.dp(4),ui.dp(4),w,h,ui.dp(4),ui.dp(4),paint);
      paint.setShader(new LinearGradient(0,0,0,h,new int[]{ui.accent,0xFFFFB4A2,0xFFFFFFFF},new float[]{0,.6f,1},Shader.TileMode.CLAMP));c.drawRoundRect(0,0,w-ui.dp(5),h-ui.dp(5),ui.dp(4),ui.dp(4),paint);
      paint.setShader(null);paint.setColor(0x40C93A25);c.drawRect(ui.dp(7),0,ui.dp(11),h-ui.dp(5),paint);paint.setColor(0x80FFFFFF);c.drawRect(ui.dp(2),0,ui.dp(4),h-ui.dp(5),paint);
    }};frame.addView(art,new FrameLayout.LayoutParams(-1,-1));frame.setElevation(ui.dp(3));
    TextView label=ui.text(name,19,true);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-2);lp.setMargins(ui.dp(22),ui.dp(28),ui.dp(16),0);frame.addView(label,lp);
    if(!guide){TextView number=ui.text(String.format(Locale.US,"%02d",count),13,false);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);np.setMargins(ui.dp(22),0,0,ui.dp(22));frame.addView(number,np);}
    View mark=ui.icon(guide?R.drawable.ic_constellate_play:R.drawable.ic_constellate_dots,guide?22:18,ui.ink);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(ui.dp(guide?22:18),ui.dp(guide?22:18),Gravity.BOTTOM|Gravity.END);mp.setMargins(0,0,ui.dp(16),ui.dp(20));frame.addView(mark,mp);
    for(int i=0;i<frame.getChildCount();i++)frame.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return frame;
  }
  private View newBook(Runnable click){
    FrameLayout frame=new FrameLayout(activity);GradientDrawable bg=ui.shape(ui.surface,ui.accent,5,0);bg.setStroke(ui.dp(1),ui.accent,ui.dp(5),ui.dp(5));frame.setBackground(bg);
    frame.setContentDescription("New word book");frame.setFocusable(true);frame.setOnClickListener(v->click.run());frame.addView(ui.icon(R.drawable.ic_constellate_plus,32,ui.accent),new FrameLayout.LayoutParams(ui.dp(32),ui.dp(32),Gravity.CENTER));return frame;
  }
  private void confirmDeleteBook(WordBooks.Book book){
    WordBooks targetBooks=books;
    LinearLayout body=ui.column();ui.padding(body,24,24);
    body.setBackground(ui.shape(ui.surface,ui.accent,20,1));
    TextView title=ui.title(body,"delete "+book.name+"?",23);title.setGravity(Gravity.CENTER);
    LinearLayout actions=ui.row();ui.add(body,actions,24);
    androidx.appcompat.widget.AppCompatButton no=deleteChoice("No",false);
    androidx.appcompat.widget.AppCompatButton yes=deleteChoice("Yes",true);
    LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,ui.dp(52),1);np.setMarginEnd(ui.dp(12));actions.addView(no,np);
    actions.addView(yes,new LinearLayout.LayoutParams(0,ui.dp(52),1));
    androidx.appcompat.app.AlertDialog dialog=new androidx.appcompat.app.AlertDialog.Builder(activity)
        .setView(body).create();
    deleteDialog=dialog;
    no.setOnClickListener(v->dialog.dismiss());
    yes.setOnClickListener(v->{
      if(books==targetBooks){
        books.delete(book);
        if(book.id.equals(bookId)){bookId="";state="shelf";draft="";}
        if(active)render();
      }
      dialog.dismiss();
    });
    dialog.setOnDismissListener(d->{if(deleteDialog==dialog)deleteDialog=null;});
    dialog.show();
    if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
  }
  private androidx.appcompat.widget.AppCompatButton deleteChoice(String label,boolean primary){
    androidx.appcompat.widget.AppCompatButton button=new androidx.appcompat.widget.AppCompatButton(activity);
    ui.styleTextTree(button);button.setText(label);button.setAllCaps(false);button.setTextSize(17);
    button.setTextColor(primary?Color.WHITE:ui.ink);button.setBackgroundTintList(null);
    button.setBackground(ui.shape(primary?ui.accent:ui.surface,primary?ui.accent:ui.line,12,1));
    return button;
  }

  private void nameBook(){
    final EditText name=new EditText(activity);name.setSingleLine();name.setTextSize(19);name.setTextColor(ui.ink);ui.styleTextTree(name);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(28)});ui.padding(name,16,12);name.setBackground(ui.shape(ui.bg,ui.accent,15,1));name.setContentDescription("Book name");
    LinearLayout body=ui.column();ui.padding(body,24,30);GradientDrawable background=ui.shape(ui.surface,ui.surface,28,0);background.setCornerRadii(new float[]{ui.dp(28),ui.dp(28),ui.dp(28),ui.dp(28),0,0,0,0});body.setBackground(background);
    ui.title(body,"New word book",25);ui.gap(body,12);ui.label(body,"Book name");ui.add(body,name,10);
    TextView create=ui.text("Create book",16,true);create.setGravity(Gravity.CENTER);create.setTextColor(0xFFFFFFFF);create.setBackground(ui.shape(ui.accent,ui.accent,14,0));create.setFocusable(true);ui.add(body,create,24);create.getLayoutParams().height=ui.dp(52);
    TextView cancel=ui.text("Cancel",15,false);cancel.setGravity(Gravity.CENTER);cancel.setTextColor(ui.muted);cancel.setFocusable(true);ui.add(body,cancel,8);cancel.getLayoutParams().height=ui.dp(44);
    BottomSheetDialog dialog=new BottomSheetDialog(activity);nameDialog=dialog;dialog.setContentView(body);cancel.setOnClickListener(v->dialog.dismiss());
    create.setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("Book name");return;}WordBooks.Book book=books.create(value);bookId=book.id;state="book";draft="";dialog.dismiss();render();if(input!=null){input.requestFocus();input.post(()->{if(input!=null)((android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});}});
    dialog.setOnDismissListener(d->{if(nameDialog==dialog)nameDialog=null;});dialog.setOnShowListener(d->{View sheet=dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);if(sheet!=null)sheet.setBackgroundColor(Color.TRANSPARENT);name.requestFocus();dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);});dialog.show();
  }
  private void addBar(boolean demonstration){
    LinearLayout bar=ui.row();ui.padding(bar,7,7);bar.setBackground(ui.shape(ui.surface,ui.surface,22,0));
    input=new EditText(activity);input.setSingleLine();input.setTextSize(17);input.setHint("Add a word");input.setContentDescription("Add a word");input.setHintTextColor(ui.muted);input.setTextColor(ui.ink);ui.styleTextTree(input);ui.padding(input,11,8);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});input.setImeOptions(EditorInfo.IME_ACTION_DONE);input.setBackground(ui.shape(ui.surface,ui.surface,15,0));
    input.setOnFocusChangeListener((v,focus)->v.setBackground(ui.shape(ui.surface,focus?ui.accent:ui.surface,15,focus?2:0)));
    bar.addView(input,new LinearLayout.LayoutParams(0,ui.dp(44),1));input.setText(demonstration?"cat":draft);
    if(!demonstration)input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){draft=s.toString();}public void afterTextChanged(Editable e){}});
    TextView add=ui.text("Add word",16,true);add.setContentDescription("Add word");add.setGravity(Gravity.CENTER);add.setTextColor(0xFFFFFFFF);add.setBackground(ui.shape(ui.accent,ui.accent,15,0));ui.padding(add,16,0);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-2,ui.dp(44));ap.setMarginStart(ui.dp(8));bar.addView(add,ap);add.setFocusable(true);add.setOnClickListener(v->{if(!demonstration)addWord();});input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE){if(!demonstration)addWord();return true;}return false;});if(demonstration){input.setFocusable(false);input.setCursorVisible(false);}ui.add(root,bar,0);
  }
  private void addWord(){
    if(pending||library==null||current()==null)return;
    final String value=ConstellateRules.word(input.getText().toString());final WordBooks.Book book=current();
    if(!ConstellateRules.validWord(value)){input.setError("Use 1–8 English letters.");return;}
    if(book.words.contains(value)){toast("Already added");return;}
    if(library.words.contains(value)){books.add(book,value);draft="";input.setText("");updateRows();return;}
    pending=true;ConstellateWords target=library;WordBooks targetBooks=books;
    target.write(value,false,ok->{if(library!=target||books!=targetBooks)return;pending=false;if(ok){books.add(book,value);draft="";if(active&&input!=null)input.setText("");if(active)updateRows();}else toast("Could not save the word. Try again.");});
  }
  private void updateRows(){
    if(rows==null||current()==null)return;rows.removeAllViews();
    for(String value:new ArrayList<>(current().words)){SwipeWordRow row=wordRow(value,false);ui.add(rows,row,rows.getChildCount()==0?0:10);}
  }
  private ImageButton icon(int resource,String label,Runnable action){ImageButton b=new ImageButton(activity);b.setImageResource(resource);b.setImageTintList(android.content.res.ColorStateList.valueOf(ui.ink));b.setBackground(ui.shape(ui.surface,ui.line,10,1));ui.padding(b,8,8);b.setContentDescription(label);b.setOnClickListener(v->action.run());return b;}
  private SwipeWordRow wordRow(String value,boolean demonstration){
    LinearLayout card=ui.row();ui.padding(card,8,12);card.setBackground(ui.shape(ui.soft,ui.accent,12,1));card.setMinimumHeight(ui.dp(90));card.setFocusable(true);card.setContentDescription(value+", "+value.length()+" letters, Grade 1");
    LinearLayout copy=ui.column();card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView name=ui.text(value,22,true);name.setMinHeight(ui.dp(48));name.setGravity(Gravity.CENTER_VERTICAL);name.setSingleLine();TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(name,16,22,1,android.util.TypedValue.COMPLEX_UNIT_SP);ui.add(copy,name,0);TextView meta=ui.text(value.length()+" letters · Grade 1",12,false);meta.setTextColor(ui.muted);meta.setSingleLine();ui.add(copy,meta,2);
    int[] icons={R.drawable.ic_constellate_volume,R.drawable.ic_constellate_bluetooth,R.drawable.ic_constellate_dots};String[] names={"Speak "+value,"Send "+value+" to braille display","View "+value+" in braille"};
    for(int i=0;i<3;i++){final int action=i;ImageButton b=icon(icons[i],names[i],()->{if(demonstration)return;if(action==0)host.speak(value);else if(action==1){host.hideKeyboard();host.send(value);}else{host.hideKeyboard();word=value;state="braille";render();}});LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(ui.dp(40),ui.dp(48));bp.setMarginStart(ui.dp(10));card.addView(b,bp);}
    return new SwipeWordRow(ui,card,value,()->{if(!demonstration)removeWord(value);});
  }
  private void removeWord(String value){
    final WordBooks.Book book=current();if(book==null||deleting.contains(value))return;
    if(books.usedElsewhere(book,value)){books.remove(book,value);updateRows();return;}
    deleting.add(value);ConstellateWords target=library;WordBooks targetBooks=books;
    target.write(value,true,ok->{deleting.remove(value);if(library!=target||books!=targetBooks)return;if(ok){books.remove(book,value);if(active)updateRows();}else toast("Could not delete the word. Try again.");});
  }
  private void braille(String value){
    LinearLayout panel=ui.panel(root);ui.title(panel,value,36);ui.body(panel,"Grade 1");View dots=new BrailleCellsView(ui,value,false);dots.setContentDescription("Guide braille preview");ui.add(panel,dots,20);
    ui.action(root,"Send to braille display",R.drawable.ic_constellate_bluetooth,true,()->{if(guideStage<0)host.send(value);});
  }
  private void startGuide(){host.hideKeyboard();guideStage=0;advanceGuide();}
  private void advanceGuide(){
    if(!active||guideStage<0)return;render();
    String[] speech={"Tap plus on the shelf to create your first word book.","Name your word book, then create it.","Type a word and tap Add word.","Your words appear below the Add word bar.","Swipe a word to the right, then tap Delete.","Tap Bluetooth to send a word. If disconnected, the original Bluetooth connection screen opens.","Tap the braille dots to view six dot braille.","Your library is ready. Create your own word book."};
    String text=speech[guideStage];host.speak(text);int stage=guideStage;
    handler.postDelayed(()->{if(!active||guideStage!=stage)return;if(++guideStage>=speech.length){guideStage=-1;state="shelf";host.stopSpeech();render();}else advanceGuide();},Math.max(4000,text.length()*75L));
  }
  private View findGuideTarget(View view,String description){
    if(description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findGuideTarget(group.getChildAt(i),description);if(found!=null)return found;}}
    return null;
  }
  private void renderGuide(){
    title("Starter guide",true);
    if(guideStage==0||guideStage==7){shelves(true);View last=root.getChildAt(2);if(last!=null)last.animate().alpha(.65f).setDuration(800).withEndAction(()->last.animate().alpha(1f).setDuration(800).start()).start();}
    else if(guideStage==1){LinearLayout panel=ui.panel(root);ui.title(panel,"New word book",25);ui.label(panel,"Book name");TextView name=ui.text("Everyday",19,false);ui.padding(name,16,16);name.setBackground(ui.shape(ui.bg,ui.accent,15,1));ui.add(panel,name,10);ui.action(panel,"Create book",0,true,()->{});}
    else if(guideStage==6){braille("cat");}
    else{addBar(true);LinearLayout list=ui.column();ui.add(root,list,24);for(String value:new String[]{"cat","sun","dog"}){SwipeWordRow row=wordRow(value,true);ui.add(list,row,list.getChildCount()==0?0:10);if(guideStage==4&&value.equals("cat"))handler.postDelayed(row::demonstrate,350);if(guideStage==5&&value.equals("cat")){View card=row.getChildAt(1);View bluetooth=((LinearLayout)card).getChildAt(2);bluetooth.animate().alpha(.3f).setDuration(800).withEndAction(()->bluetooth.animate().alpha(1).setDuration(800).start()).start();}}}
    String[] targets={"New word book","Create book","Add word","cat, 3 letters, Grade 1","Delete cat","Send cat to braille display","Guide braille preview","New word book"};
    View target=findGuideTarget(root,targets[guideStage]);
    if(target!=null){
      LinearLayout body=ui.column();
      while(root.getChildCount()>0){View child=root.getChildAt(0);root.removeView(child);body.addView(child);}
      GuidePointer overlay=new GuidePointer(ui,target);
      overlay.addView(body,new FrameLayout.LayoutParams(-1,-2));
      ui.add(root,overlay,0);
    }
  }
}
